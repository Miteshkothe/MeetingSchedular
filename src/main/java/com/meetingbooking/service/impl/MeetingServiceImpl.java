package com.meetingbooking.service.impl;

import com.meetingbooking.conflict.ConflictDetectionResult;
import com.meetingbooking.conflict.ConflictDetectionService;
import com.meetingbooking.dto.*;
import com.meetingbooking.entity.*;
import com.meetingbooking.exception.ResourceNotFoundException;
import com.meetingbooking.exception.InvalidRecurrenceRuleException;
import com.meetingbooking.exception.RoomConflictException;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.recurrence.RecurrenceService;
import com.meetingbooking.recurrence.TimezoneService;
import com.meetingbooking.repository.*;
import com.meetingbooking.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingSeriesRepository seriesRepository;
    private final MeetingOccurrenceRepository occurrenceRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RecurrenceService recurrenceService;
    private final TimezoneService timezoneService;
    private final ConflictDetectionService conflictDetectionService;
    private final MeterRegistry meterRegistry;

    @Value("${app.meeting.rolling-horizon-months:12}")
    private int rollingHorizonMonths;

    @Override
    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public MeetingSeriesResponse createMeeting(CreateMeetingRequest request, Long currentUserId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        meterRegistry.counter("booking.requests.total").increment();
        try {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getRoomId()));

        User organizer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with id: " + currentUserId));

        // Validate time order
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("End time must be strictly after start time.");
        }

        List<Long> attendeeIds = request.getAttendeeUserIds() == null ? List.of() : request.getAttendeeUserIds().stream().distinct().toList();
        if (attendeeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Attendee user ids cannot contain null values.");
        }
        if (attendeeIds.contains(currentUserId)) {
            throw new IllegalArgumentException("The organizer must not be listed as an attendee.");
        }
        // Validate capacity against unique, real users rather than raw submitted ids.
        int requestedAttendees = attendeeIds.size() + 1;
        if (requestedAttendees > room.getCapacity()) {
            throw new IllegalArgumentException(String.format("Room '%s' capacity (%d) exceeded by requested attendees (%d).",
                    room.getName(), room.getCapacity(), requestedAttendees));
        }

        // Validate timezone
        ZoneId zoneId = timezoneService.parseZoneId(request.getTimezone());

        // Determine rolling horizon
        LocalDate recurrenceEnd = request.getRecurrenceRule() == null ? null : request.getRecurrenceRule().getEndDate();
        if (request.getRecurrenceRule() != null && request.getEndDate() != null
                && (recurrenceEnd == null || request.getEndDate().isBefore(recurrenceEnd))) {
            recurrenceEnd = request.getEndDate();
        }
        if (request.getRecurrenceRule() != null && recurrenceEnd != null
                && recurrenceEnd.isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Meeting end date must not be before its start date.");
        }
        LocalDate horizonEnd = LocalDate.now(zoneId).plusMonths(rollingHorizonMonths);
        if (recurrenceEnd != null && recurrenceEnd.isBefore(horizonEnd)) {
            horizonEnd = recurrenceEnd;
        }
        if (request.getRecurrenceRule() == null) {
            horizonEnd = request.getStartDate();
        }

        // Build series and rule
        RecurrenceRule rule = null;
        if (request.getRecurrenceRule() != null) {
            rule = mapToRecurrenceRuleEntity(request.getRecurrenceRule());
            rule.setEndDate(recurrenceEnd);
            validateRecurrenceRule(rule);
        }

        MeetingSeries series = MeetingSeries.builder()
                .room(room)
                .organizer(organizer)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .timezone(zoneId.getId())
                .startDate(request.getStartDate())
                .endDate(rule == null ? request.getEndDate() : recurrenceEnd)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .recurrenceRule(rule)
                .status(SeriesStatus.ACTIVE)
                .horizonEnd(horizonEnd)
                .build();

        // Generate occurrence proposals
        LocalDate firstMaterializedDate = request.getStartDate().isAfter(LocalDate.now(zoneId))
                ? request.getStartDate() : LocalDate.now(zoneId);
        List<OccurrenceProposal> proposals = recurrenceService.generateOccurrences(
                series, firstMaterializedDate, horizonEnd
        );

        if (proposals.isEmpty() && (rule == null || !request.getStartDate().isAfter(horizonEnd))) {
            throw new IllegalArgumentException("Recurrence rule produced zero occurrences within the horizon.");
        }

        // Batch conflict validation
        ConflictDetectionResult conflictResult = conflictDetectionService.batchCheckConflicts(
                room.getId(), proposals, null
        );

        if (conflictResult.hasConflict()) {
            log.warn("Room conflict detected while booking room {} for {} occurrences", room.getName(), proposals.size());
            throw new RoomConflictException("Room is already booked for one or more requested occurrences.", conflictResult.conflicts());
        }

        // Build concrete occurrences
        List<MeetingOccurrence> occurrences = new ArrayList<>();
        for (OccurrenceProposal proposal : proposals) {
            MeetingOccurrence occurrence = MeetingOccurrence.builder()
                    .series(series)
                    .room(room)
                    .startTimeUtc(proposal.startTimeUtc())
                    .endTimeUtc(proposal.endTimeUtc())
                    .originalLocalDate(proposal.originalLocalDate())
                    .status(OccurrenceStatus.CONFIRMED)
                    .isException(false)
                    .build();
            occurrences.add(occurrence);
        }
        series.setOccurrences(occurrences);

        // Attach attendees
        if (request.getAttendeeUserIds() != null && !request.getAttendeeUserIds().isEmpty()) {
            List<User> attendeeUsers = userRepository.findAllById(attendeeIds);
            if (attendeeUsers.size() != attendeeIds.size()) {
                Set<Long> foundIds = attendeeUsers.stream().map(User::getId).collect(Collectors.toSet());
                List<Long> missingIds = attendeeIds.stream().filter(id -> !foundIds.contains(id)).toList();
                throw new ResourceNotFoundException("Attendee user ids not found: " + missingIds);
            }
            List<MeetingAttendee> seriesAttendees = attendeeUsers.stream()
                    .map(u -> MeetingAttendee.builder()
                            .series(series)
                            .user(u)
                            .attendanceStatus(AttendanceStatus.INVITED)
                            .build())
                    .collect(Collectors.toList());
            series.setAttendees(seriesAttendees);
        }

        try {
            MeetingSeries savedSeries = seriesRepository.save(series);
            log.info("Successfully created meeting series id: {} with {} occurrences", savedSeries.getId(), occurrences.size());
            return mapToSeriesResponse(savedSeries);
        } catch (DataIntegrityViolationException ex) {
            log.error("Exclusion constraint collision or uniqueness failure on database commit: {}", ex.getMessage());
            throw new RoomConflictException("Room reservation failed due to concurrent booking conflict. Please choose another time.", List.of());
        }
        } catch (RoomConflictException ex) {
            meterRegistry.counter("booking.conflicts.total").increment();
            meterRegistry.counter("booking.failures.total").increment();
            throw ex;
        } catch (RuntimeException ex) {
            meterRegistry.counter("booking.failures.total").increment();
            throw ex;
        } finally {
            sample.stop(meterRegistry.timer("booking.latency"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingSeriesResponse getMeetingById(Long seriesId, Long userId, boolean isAdmin) {
        MeetingSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting series not found with id: " + seriesId));
        verifyUserAuthorization(series, userId, isAdmin);
        return mapToSeriesResponse(series);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingOccurrenceResponse getOccurrenceById(Long occurrenceId, Long userId, boolean isAdmin) {
        MeetingOccurrence occurrence = occurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting occurrence not found with id: " + occurrenceId));
        verifyUserAuthorization(occurrence.getSeries(), userId, isAdmin);
        return mapToOccurrenceResponse(occurrence);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeetingOccurrenceResponse> getCalendarOccurrences(Instant from, Instant to, Long roomId, Long userId, boolean isAdmin, Pageable pageable) {
        validateRange(from, to);
        return occurrenceRepository.findCalendarOccurrences(roomId, isAdmin ? null : userId, from, to, pageable)
                .map(this::mapToOccurrenceResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "availability", key = "#roomId + ':' + #from + ':' + #to")
    public RoomAvailabilityResponse getRoomAvailability(Long roomId, Instant from, Instant to) {
        validateRange(from, to);
        if (!roomRepository.existsById(roomId)) {
            throw new ResourceNotFoundException("Room not found with id: " + roomId);
        }
        List<RoomAvailabilityResponse.BusyInterval> overlaps = occurrenceRepository
                .findConflictingOccurrences(roomId, from, to, null).stream()
                .map(o -> RoomAvailabilityResponse.BusyInterval.builder()
                        .start(o.getStartTimeUtc()).end(o.getEndTimeUtc()).build())
                .toList();
        return RoomAvailabilityResponse.builder().roomId(roomId).requestedStart(from).requestedEnd(to)
                .available(overlaps.isEmpty()).conflictingIntervals(overlaps).build();
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("A valid range is required: end must be after start.");
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public MeetingOccurrenceResponse editOccurrence(
            Long seriesId,
            Long occurrenceId,
            EditOccurrenceRequest request,
            Long currentUserId,
            boolean isAdmin
    ) {
        MeetingOccurrence occurrence = occurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting occurrence not found with id: " + occurrenceId));

        MeetingSeries series = occurrence.getSeries();
        if (!series.getId().equals(seriesId)) {
            throw new IllegalArgumentException("Occurrence id " + occurrenceId + " does not belong to series id " + seriesId);
        }

        verifyUserAuthorization(series, currentUserId, isAdmin);

        return switch (request.getMode()) {
            case THIS -> editThisOccurrence(occurrence, request);
            case THIS_AND_FUTURE -> editThisAndFutureOccurrences(occurrence, request);
            case WHOLE_SERIES -> editWholeSeries(series, occurrence, request);
        };
    }

    private MeetingOccurrenceResponse editThisOccurrence(MeetingOccurrence occurrence, EditOccurrenceRequest request) {
        Room targetRoom = occurrence.getRoom();
        if (request.getNewRoomId() != null) {
            targetRoom = roomRepository.findById(request.getNewRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getNewRoomId()));
        }

        LocalDate targetDate = request.getNewLocalDate() != null ? request.getNewLocalDate() : occurrence.getOriginalLocalDate();
        LocalTime startTime = request.getNewStartTime() != null ? request.getNewStartTime() : occurrence.getSeries().getStartTime();
        LocalTime endTime = request.getNewEndTime() != null ? request.getNewEndTime() : occurrence.getSeries().getEndTime();

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be strictly after start time.");
        }

        ZoneId zoneId = timezoneService.parseZoneId(occurrence.getSeries().getTimezone());
        Instant newStartUtc = timezoneService.toUtcInstant(targetDate, startTime, zoneId);
        Instant newEndUtc = timezoneService.toUtcInstant(targetDate, endTime, zoneId);

        // Check single conflict excluding this occurrence
        ConflictDetectionResult conflict = conflictDetectionService.checkSingleConflict(
                targetRoom.getId(), newStartUtc, newEndUtc, occurrence.getId()
        );

        if (conflict.hasConflict()) {
            throw new RoomConflictException("Room conflict detected for modified occurrence.", conflict.conflicts());
        }

        occurrence.setRoom(targetRoom);
        occurrence.setStartTimeUtc(newStartUtc);
        occurrence.setEndTimeUtc(newEndUtc);
        occurrence.setOriginalLocalDate(targetDate);
        occurrence.setException(true);

        try {
            MeetingOccurrence saved = occurrenceRepository.save(occurrence);
            log.info("Updated single occurrence id: {} as an exception", saved.getId());
            return mapToOccurrenceResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new RoomConflictException("Concurrent booking collision occurred for edited occurrence.", List.of());
        }
    }

    private MeetingOccurrenceResponse editThisAndFutureOccurrences(MeetingOccurrence occurrence, EditOccurrenceRequest request) {
        MeetingSeries oldSeries = occurrence.getSeries();
        LocalDate splitDate = occurrence.getOriginalLocalDate();
        RecurrenceRule originalRule = cloneRecurrenceRule(oldSeries.getRecurrenceRule());

        // 1. Find all future occurrences in old series starting on or after splitDate
        List<MeetingOccurrence> futureOccurrences = occurrenceRepository.findFutureOccurrencesFromDate(oldSeries.getId(), splitDate);

        // 2. Mark future occurrences in old series as CANCELLED to preserve history
        for (MeetingOccurrence fut : futureOccurrences) {
            fut.setStatus(OccurrenceStatus.CANCELLED);
        }
        occurrenceRepository.saveAll(futureOccurrences);
        occurrenceRepository.flush();

        // 3. Truncate old series end date
        LocalDate oldSeriesNewEnd = splitDate.minusDays(1);
        if (oldSeriesNewEnd.isBefore(oldSeries.getStartDate())) {
            oldSeries.setStatus(SeriesStatus.CANCELLED);
        } else {
            oldSeries.setEndDate(oldSeriesNewEnd);
            oldSeries.setHorizonEnd(oldSeriesNewEnd);
            if (oldSeries.getRecurrenceRule() != null) {
                LocalDate ruleEnd = oldSeries.getRecurrenceRule().getEndDate();
                oldSeries.getRecurrenceRule().setEndDate(ruleEnd == null || oldSeriesNewEnd.isBefore(ruleEnd)
                        ? oldSeriesNewEnd : ruleEnd);
            }
        }
        seriesRepository.save(oldSeries);

        // 4. Create new series starting from splitDate
        Room targetRoom = request.getNewRoomId() != null
                ? roomRepository.findById(request.getNewRoomId()).orElseThrow(() -> new ResourceNotFoundException("Room not found"))
                : oldSeries.getRoom();

        LocalTime startTime = request.getNewStartTime() != null ? request.getNewStartTime() : oldSeries.getStartTime();
        LocalTime endTime = request.getNewEndTime() != null ? request.getNewEndTime() : oldSeries.getEndTime();

        RecurrenceRule newRule;
        if (request.getNewRecurrenceRule() != null) {
            newRule = mapToRecurrenceRuleEntity(request.getNewRecurrenceRule());
            validateRecurrenceRule(newRule);
        } else {
            newRule = originalRule;
        }

        LocalDate newHorizonEnd = splitDate.plusMonths(rollingHorizonMonths);
        if (newRule != null && newRule.getEndDate() != null && newRule.getEndDate().isBefore(newHorizonEnd)) {
            newHorizonEnd = newRule.getEndDate();
        }
        if (newHorizonEnd.isBefore(splitDate)) {
            throw new IllegalArgumentException("The new recurrence ends before the selected split occurrence.");
        }

        MeetingSeries newSeries = MeetingSeries.builder()
                .room(targetRoom)
                .organizer(oldSeries.getOrganizer())
                .title(request.getNewTitle() != null ? request.getNewTitle() : oldSeries.getTitle())
                .description(request.getNewDescription() != null ? request.getNewDescription() : oldSeries.getDescription())
                .timezone(oldSeries.getTimezone())
                .startDate(splitDate)
                .endDate(newRule != null ? newRule.getEndDate() : null)
                .startTime(startTime)
                .endTime(endTime)
                .recurrenceRule(newRule)
                .status(SeriesStatus.ACTIVE)
                .horizonEnd(newHorizonEnd)
                .build();

        if (oldSeries.getAttendees() != null && !oldSeries.getAttendees().isEmpty()) {
            newSeries.setAttendees(oldSeries.getAttendees().stream().map(a -> MeetingAttendee.builder()
                    .series(newSeries).user(a.getUser()).attendanceStatus(a.getAttendanceStatus()).build()).toList());
        }
        if (newSeries.getAttendees().size() + 1 > targetRoom.getCapacity()) {
            throw new IllegalArgumentException("The selected room cannot accommodate all meeting attendees.");
        }

        // 5. Generate occurrences for new series and batch validate conflicts
        List<OccurrenceProposal> proposals = recurrenceService.generateOccurrences(newSeries, splitDate, newHorizonEnd);
        ConflictDetectionResult conflict = conflictDetectionService.batchCheckConflicts(targetRoom.getId(), proposals, oldSeries.getId());
        if (conflict.hasConflict()) {
            throw new RoomConflictException("Conflict detected while splitting recurring series.", conflict.conflicts());
        }

        List<MeetingOccurrence> newOccurrences = proposals.stream()
                .map(p -> MeetingOccurrence.builder()
                        .series(newSeries)
                        .room(targetRoom)
                        .startTimeUtc(p.startTimeUtc())
                        .endTimeUtc(p.endTimeUtc())
                        .originalLocalDate(p.originalLocalDate())
                        .status(OccurrenceStatus.CONFIRMED)
                        .isException(false)
                        .build())
                .toList();

        newSeries.setOccurrences(newOccurrences);
        MeetingSeries savedNewSeries = seriesRepository.save(newSeries);

        log.info("Split series {} at {}. New series id: {}", oldSeries.getId(), splitDate, savedNewSeries.getId());
        return mapToOccurrenceResponse(savedNewSeries.getOccurrences().get(0));
    }

    private MeetingOccurrenceResponse editWholeSeries(MeetingSeries series, MeetingOccurrence triggerOccurrence, EditOccurrenceRequest request) {
        Room targetRoom = request.getNewRoomId() != null
                ? roomRepository.findById(request.getNewRoomId()).orElseThrow(() -> new ResourceNotFoundException("Room not found"))
                : series.getRoom();

        if (request.getNewTitle() != null) {
            series.setTitle(request.getNewTitle());
        }
        if (request.getNewDescription() != null) {
            series.setDescription(request.getNewDescription());
        }
        if (request.getNewStartTime() != null) {
            series.setStartTime(request.getNewStartTime());
        }
        if (request.getNewEndTime() != null) {
            series.setEndTime(request.getNewEndTime());
        }
        if (!series.getEndTime().isAfter(series.getStartTime())) {
            throw new IllegalArgumentException("End time must be strictly after start time.");
        }
        if (request.getNewRecurrenceRule() != null) {
            RecurrenceRule updatedRule = mapToRecurrenceRuleEntity(request.getNewRecurrenceRule());
            validateRecurrenceRule(updatedRule);
            series.setRecurrenceRule(updatedRule);
            series.setEndDate(updatedRule.getEndDate());
        }
        int attendeeCount = series.getAttendees().size() + 1;
        if (attendeeCount > targetRoom.getCapacity()) {
            throw new IllegalArgumentException("The selected room cannot accommodate all meeting attendees.");
        }
        series.setRoom(targetRoom);

        Instant nowUtc = Instant.now();
        if (!series.getEndTime().isAfter(series.getStartTime())) {
            throw new IllegalArgumentException("End time must be strictly after start time.");
        }
        LocalDate fromDate = LocalDate.now();
        if (fromDate.isBefore(series.getStartDate())) {
            fromDate = series.getStartDate();
        }

        List<OccurrenceProposal> proposals;
        if (!series.isRecurring() && triggerOccurrence.getStartTimeUtc().isAfter(nowUtc)) {
            proposals = List.of(recurrenceService.createProposal(triggerOccurrence.getOriginalLocalDate(),
                    series.getStartTime(), series.getEndTime(), series.getTimezone()));
        } else {
            proposals = recurrenceService.generateOccurrences(series, fromDate, series.getHorizonEnd());
        }
        if (proposals.isEmpty()) {
            throw new IllegalArgumentException("The updated recurrence produces no occurrences in the current horizon.");
        }
        ConflictDetectionResult conflict = conflictDetectionService.batchCheckConflicts(targetRoom.getId(), proposals, series.getId());
        if (conflict.hasConflict()) {
            throw new RoomConflictException("Conflict detected while updating whole series.", conflict.conflicts());
        }

        List<MeetingOccurrence> futureOccurrences = series.getOccurrences().stream()
                .filter(o -> o.getStartTimeUtc().isAfter(nowUtc) && o.getStatus() != OccurrenceStatus.CANCELLED)
                .toList();
        Map<Instant, MeetingOccurrence> reusableByStart = new HashMap<>();
        for (MeetingOccurrence existing : futureOccurrences) {
            existing.setStatus(OccurrenceStatus.CANCELLED);
            reusableByStart.put(existing.getStartTimeUtc(), existing);
        }
        occurrenceRepository.saveAll(futureOccurrences);
        occurrenceRepository.flush();

        List<MeetingOccurrence> responseOccurrences = new ArrayList<>();
        for (OccurrenceProposal p : proposals) {
            MeetingOccurrence occurrence = reusableByStart.remove(p.startTimeUtc());
            if (occurrence == null) {
                occurrence = MeetingOccurrence.builder().series(series).build();
                series.getOccurrences().add(occurrence);
            }
            occurrence.setRoom(targetRoom);
            occurrence.setStartTimeUtc(p.startTimeUtc());
            occurrence.setEndTimeUtc(p.endTimeUtc());
            occurrence.setOriginalLocalDate(p.originalLocalDate());
            occurrence.setStatus(OccurrenceStatus.CONFIRMED);
            occurrence.setException(false);
            responseOccurrences.add(occurrence);
        }

        MeetingSeries savedSeries = seriesRepository.save(series);
        log.info("Updated whole series id: {}", savedSeries.getId());
        MeetingOccurrence result = responseOccurrences.stream()
                .filter(o -> o.getOriginalLocalDate().equals(triggerOccurrence.getOriginalLocalDate()))
                .findFirst().orElse(responseOccurrences.get(0));
        return mapToOccurrenceResponse(result);
    }

    @Override
    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public void cancelMeeting(Long seriesId, Long occurrenceId, CancelMode mode, Long currentUserId, boolean isAdmin) {
        MeetingSeries series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting series not found with id: " + seriesId));

        verifyUserAuthorization(series, currentUserId, isAdmin);

        switch (mode) {
            case ONE -> {
                MeetingOccurrence occ = occurrenceRepository.findById(occurrenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Occurrence not found with id: " + occurrenceId));
                ensureOccurrenceBelongsToSeries(series, occ);
                occ.setStatus(OccurrenceStatus.CANCELLED);
                occurrenceRepository.save(occ);
                log.info("Cancelled occurrence id: {}", occurrenceId);
            }
            case THIS_AND_FUTURE -> {
                MeetingOccurrence occ = occurrenceRepository.findById(occurrenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Occurrence not found with id: " + occurrenceId));
                ensureOccurrenceBelongsToSeries(series, occ);
                LocalDate fromDate = occ.getOriginalLocalDate();
                List<MeetingOccurrence> futureOccurrences = occurrenceRepository.findFutureOccurrencesFromDate(seriesId, fromDate);
                for (MeetingOccurrence o : futureOccurrences) {
                    o.setStatus(OccurrenceStatus.CANCELLED);
                }
                occurrenceRepository.saveAll(futureOccurrences);

                LocalDate newEndDate = fromDate.minusDays(1);
                if (newEndDate.isBefore(series.getStartDate())) {
                    series.setStatus(SeriesStatus.CANCELLED);
                } else {
                    series.setEndDate(newEndDate);
                    series.setHorizonEnd(newEndDate);
                    if (series.getRecurrenceRule() != null) {
                        LocalDate ruleEnd = series.getRecurrenceRule().getEndDate();
                        series.getRecurrenceRule().setEndDate(ruleEnd == null || newEndDate.isBefore(ruleEnd)
                                ? newEndDate : ruleEnd);
                    }
                }
                seriesRepository.save(series);
                log.info("Cancelled this and future occurrences from {} for series {}", fromDate, seriesId);
            }
            case WHOLE_SERIES -> {
                series.setStatus(SeriesStatus.CANCELLED);
                for (MeetingOccurrence o : series.getOccurrences()) {
                    if (o.getStartTimeUtc().isAfter(Instant.now())) {
                        o.setStatus(OccurrenceStatus.CANCELLED);
                    }
                }
                seriesRepository.save(series);
                log.info("Cancelled entire series id: {}", seriesId);
            }
        }
    }

    private void verifyUserAuthorization(MeetingSeries series, Long currentUserId, boolean isAdmin) {
        if (!isAdmin && !series.getOrganizer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You are not authorized to modify this meeting series.");
        }
    }

    private void ensureOccurrenceBelongsToSeries(MeetingSeries series, MeetingOccurrence occurrence) {
        if (occurrence.getSeries() == null || !series.getId().equals(occurrence.getSeries().getId())) {
            throw new ResourceNotFoundException("Occurrence not found in the requested meeting series.");
        }
    }

    private RecurrenceRule mapToRecurrenceRuleEntity(RecurrenceRuleDto dto) {
        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(dto.getFrequency())
                .intervalValue(dto.getIntervalValue() != null ? dto.getIntervalValue() : 1)
                .dayOfMonth(dto.getDayOfMonth())
                .weekNumber(dto.getWeekNumber())
                .weekday(dto.getWeekday())
                .endDate(dto.getEndDate())
                .occurrenceCount(dto.getOccurrenceCount())
                .build();
        rule.setWeekdaysSet(dto.getWeekdays());
        return rule;
    }

    private void validateRecurrenceRule(RecurrenceRule rule) {
        if (rule.getFrequency() == null) {
            throw new InvalidRecurrenceRuleException("Recurrence frequency is required.");
        }
        if (rule.getIntervalValue() == null || rule.getIntervalValue() < 1 || rule.getIntervalValue() > 104) {
            throw new InvalidRecurrenceRuleException("Recurrence interval must be between 1 and 104.");
        }
        if (rule.getFrequency() == RecurrenceFrequency.MONTHLY) {
            boolean fixedDay = rule.getDayOfMonth() != null;
            boolean nthWeekday = rule.getWeekNumber() != null || rule.getWeekday() != null;
            if (fixedDay == nthWeekday) {
                throw new InvalidRecurrenceRuleException(
                        "Monthly recurrence requires either dayOfMonth or both weekNumber and weekday.");
            }
            if (nthWeekday) {
                if (rule.getWeekNumber() == null || rule.getWeekday() == null
                        || (rule.getWeekNumber() != -1 && (rule.getWeekNumber() < 1 || rule.getWeekNumber() > 5))) {
                    throw new InvalidRecurrenceRuleException("Nth weekday requires weekNumber 1-5 or -1 and a weekday.");
                }
                try { DayOfWeek.valueOf(rule.getWeekday().trim().toUpperCase(Locale.ROOT)); }
                catch (RuntimeException e) { throw new InvalidRecurrenceRuleException("Invalid monthly weekday: " + rule.getWeekday()); }
            }
            if (fixedDay && (rule.getDayOfMonth() < 1 || rule.getDayOfMonth() > 31)) {
                throw new InvalidRecurrenceRuleException("dayOfMonth must be between 1 and 31.");
            }
        } else if (rule.getDayOfMonth() != null || rule.getWeekNumber() != null || rule.getWeekday() != null) {
            throw new InvalidRecurrenceRuleException("Monthly-only fields cannot be used for daily or weekly recurrence.");
        }
        if (rule.getFrequency() != RecurrenceFrequency.WEEKLY && !rule.getWeekdaysSet().isEmpty()) {
            throw new InvalidRecurrenceRuleException("weekdays can only be used for weekly recurrence.");
        }
    }

    private RecurrenceRule cloneRecurrenceRule(RecurrenceRule source) {
        if (source == null) return null;
        RecurrenceRule clone = RecurrenceRule.builder()
                .frequency(source.getFrequency())
                .intervalValue(source.getIntervalValue())
                .weekdays(source.getWeekdays())
                .dayOfMonth(source.getDayOfMonth())
                .weekNumber(source.getWeekNumber())
                .weekday(source.getWeekday())
                .endDate(source.getEndDate())
                .occurrenceCount(source.getOccurrenceCount())
                .build();
        return clone;
    }

    private MeetingSeriesResponse mapToSeriesResponse(MeetingSeries series) {
        List<AttendeeDto> attendeeDtos = series.getAttendees().stream()
                .map(a -> AttendeeDto.builder()
                        .userId(a.getUser().getId())
                        .name(a.getUser().getName())
                        .email(a.getUser().getEmail())
                        .status(a.getAttendanceStatus())
                        .build())
                .toList();

        List<MeetingOccurrenceResponse> occurrenceDtos = series.getOccurrences().stream()
                .map(this::mapToOccurrenceResponse)
                .toList();

        RecurrenceRuleDto ruleDto = null;
        if (series.getRecurrenceRule() != null) {
            RecurrenceRule r = series.getRecurrenceRule();
            ruleDto = RecurrenceRuleDto.builder()
                    .frequency(r.getFrequency())
                    .intervalValue(r.getIntervalValue())
                    .weekdays(r.getWeekdaysSet())
                    .dayOfMonth(r.getDayOfMonth())
                    .weekNumber(r.getWeekNumber())
                    .weekday(r.getWeekday())
                    .endDate(r.getEndDate())
                    .occurrenceCount(r.getOccurrenceCount())
                    .build();
        }

        return MeetingSeriesResponse.builder()
                .id(series.getId())
                .roomId(series.getRoom().getId())
                .roomName(series.getRoom().getName())
                .organizerId(series.getOrganizer().getId())
                .organizerName(series.getOrganizer().getName())
                .title(series.getTitle())
                .description(series.getDescription())
                .timezone(series.getTimezone())
                .startDate(series.getStartDate())
                .endDate(series.getEndDate())
                .startTime(series.getStartTime())
                .endTime(series.getEndTime())
                .recurrenceRule(ruleDto)
                .status(series.getStatus())
                .horizonEnd(series.getHorizonEnd())
                .attendees(attendeeDtos)
                .occurrences(occurrenceDtos)
                .createdAt(series.getCreatedAt())
                .updatedAt(series.getUpdatedAt())
                .build();
    }

    private MeetingOccurrenceResponse mapToOccurrenceResponse(MeetingOccurrence occ) {
        List<AttendeeDto> attendeeDtos = occ.getAttendees().stream()
                .map(a -> AttendeeDto.builder()
                        .userId(a.getUser().getId())
                        .name(a.getUser().getName())
                        .email(a.getUser().getEmail())
                        .status(a.getAttendanceStatus())
                        .build())
                .toList();

        return MeetingOccurrenceResponse.builder()
                .id(occ.getId())
                .seriesId(occ.getSeries().getId())
                .title(occ.getSeries().getTitle())
                .description(occ.getSeries().getDescription())
                .roomId(occ.getRoom().getId())
                .roomName(occ.getRoom().getName())
                .organizerId(occ.getSeries().getOrganizer().getId())
                .organizerName(occ.getSeries().getOrganizer().getName())
                .timezone(occ.getSeries().getTimezone())
                .startTimeUtc(occ.getStartTimeUtc())
                .endTimeUtc(occ.getEndTimeUtc())
                .originalLocalDate(occ.getOriginalLocalDate())
                .status(occ.getStatus())
                .isException(occ.isException())
                .attendees(attendeeDtos)
                .createdAt(occ.getCreatedAt())
                .updatedAt(occ.getUpdatedAt())
                .build();
    }
}
