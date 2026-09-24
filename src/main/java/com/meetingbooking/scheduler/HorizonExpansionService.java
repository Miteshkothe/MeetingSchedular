package com.meetingbooking.scheduler;

import com.meetingbooking.conflict.ConflictDetectionService;
import com.meetingbooking.conflict.ConflictDetectionResult;
import com.meetingbooking.entity.*;
import com.meetingbooking.exception.ResourceNotFoundException;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.recurrence.RecurrenceService;
import com.meetingbooking.repository.MeetingOccurrenceRepository;
import com.meetingbooking.repository.MeetingSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HorizonExpansionService {
    private final MeetingSeriesRepository seriesRepository;
    private final MeetingOccurrenceRepository occurrenceRepository;
    private final RecurrenceService recurrenceService;
    private final ConflictDetectionService conflictDetectionService;

    @Value("${app.meeting.rolling-horizon-months:12}") private int rollingHorizonMonths;

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public boolean expandSeries(Long seriesId) {
        MeetingSeries series = seriesRepository.findByIdForUpdate(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting series not found: " + seriesId));
        if (series.getStatus() != SeriesStatus.ACTIVE || !series.isRecurring()) return false;

        LocalDate today = LocalDate.now(ZoneId.of(series.getTimezone()));
        LocalDate currentHorizonEnd = series.getHorizonEnd();
        LocalDate newHorizonEnd = today.plusMonths(rollingHorizonMonths);
        if (series.getRecurrenceRule().getEndDate() != null
                && series.getRecurrenceRule().getEndDate().isBefore(newHorizonEnd)) {
            newHorizonEnd = series.getRecurrenceRule().getEndDate();
        }
        if (series.getEndDate() != null && series.getEndDate().isBefore(newHorizonEnd)) {
            newHorizonEnd = series.getEndDate();
        }
        if (!newHorizonEnd.isAfter(currentHorizonEnd)) return false;

        LocalDate firstNewDate = currentHorizonEnd.plusDays(1);
        if (firstNewDate.isBefore(today)) firstNewDate = today;
        List<OccurrenceProposal> proposals = recurrenceService.generateOccurrences(series, firstNewDate, newHorizonEnd);
        ConflictDetectionResult result = conflictDetectionService.batchCheckConflicts(
                series.getRoom().getId(), proposals, seriesId);

        List<MeetingOccurrence> occurrences = new ArrayList<>();
        for (OccurrenceProposal proposal : proposals) {
            boolean conflict = result.conflicts().stream().anyMatch(c -> proposal.startTimeUtc().equals(c.requestedStart()));
            occurrences.add(MeetingOccurrence.builder().series(series).room(series.getRoom())
                    .startTimeUtc(proposal.startTimeUtc()).endTimeUtc(proposal.endTimeUtc())
                    .originalLocalDate(proposal.originalLocalDate())
                    .status(conflict ? OccurrenceStatus.CONFLICT : OccurrenceStatus.CONFIRMED)
                    .isException(false).build());
        }
        occurrenceRepository.saveAll(occurrences);
        series.setHorizonEnd(newHorizonEnd);
        seriesRepository.save(series);
        log.info("Expanded series {} horizon {} -> {} with {} occurrences ({} conflicts)", seriesId,
                currentHorizonEnd, newHorizonEnd, occurrences.size(), result.conflicts().size());
        return true;
    }
}
