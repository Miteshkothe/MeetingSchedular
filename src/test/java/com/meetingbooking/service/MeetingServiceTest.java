package com.meetingbooking.service;

import com.meetingbooking.conflict.ConflictDetail;
import com.meetingbooking.conflict.ConflictDetectionResult;
import com.meetingbooking.conflict.ConflictDetectionService;
import com.meetingbooking.dto.CancelMode;
import com.meetingbooking.dto.CreateMeetingRequest;
import com.meetingbooking.dto.EditMode;
import com.meetingbooking.dto.EditOccurrenceRequest;
import com.meetingbooking.dto.MeetingOccurrenceResponse;
import com.meetingbooking.dto.MeetingSeriesResponse;
import com.meetingbooking.dto.RecurrenceRuleDto;
import com.meetingbooking.dto.RoomAvailabilityResponse;
import com.meetingbooking.entity.*;
import com.meetingbooking.exception.RoomConflictException;
import com.meetingbooking.exception.ResourceNotFoundException;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.recurrence.RecurrenceService;
import com.meetingbooking.recurrence.TimezoneService;
import com.meetingbooking.repository.*;
import com.meetingbooking.service.impl.MeetingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingSeriesRepository seriesRepository;
    @Mock
    private MeetingOccurrenceRepository occurrenceRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecurrenceService recurrenceService;
    @Mock
    private TimezoneService timezoneService;
    @Mock
    private ConflictDetectionService conflictDetectionService;

    private MeetingServiceImpl meetingService;

    private User organizer;
    private Room room;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingServiceImpl(
                seriesRepository,
                occurrenceRepository,
                roomRepository,
                userRepository,
                recurrenceService,
                timezoneService,
                conflictDetectionService,
                new SimpleMeterRegistry()
        );
        ReflectionTestUtils.setField(meetingService, "rollingHorizonMonths", 12);

        organizer = User.builder().id(1L).name("Bob").email("bob@example.com").role(Role.ROLE_USER).build();
        room = Room.builder().id(10L).name("Boardroom").capacity(20).build();
    }

    @Test
    @DisplayName("Should successfully create a one-time meeting")
    void testCreateOneTimeMeetingSuccess() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .roomId(10L)
                .title("Strategy Discussion")
                .timezone("Asia/Kolkata")
                .startDate(LocalDate.of(2026, 9, 25))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();

        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(timezoneService.parseZoneId("Asia/Kolkata")).thenReturn(ZoneId.of("Asia/Kolkata"));

        List<OccurrenceProposal> proposals = List.of(
                new OccurrenceProposal(LocalDate.of(2026, 9, 25), Instant.parse("2026-09-25T04:30:00Z"), Instant.parse("2026-09-25T05:30:00Z"))
        );
        when(recurrenceService.generateOccurrences(any(MeetingSeries.class), any(), any()))
                .thenReturn(proposals);

        when(conflictDetectionService.batchCheckConflicts(eq(10L), eq(proposals), isNull()))
                .thenReturn(ConflictDetectionResult.noConflict());

        when(seriesRepository.save(any(MeetingSeries.class))).thenAnswer(inv -> {
            MeetingSeries s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        MeetingSeriesResponse response = meetingService.createMeeting(request, 1L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Strategy Discussion", response.getTitle());
        verify(seriesRepository).save(any(MeetingSeries.class));
    }

    @Test
    @DisplayName("Should atomically reject and rollback recurring meeting if any occurrence conflicts")
    void testCreateRecurringMeetingRollbackOnConflict() {
        CreateMeetingRequest request = CreateMeetingRequest.builder()
                .roomId(10L)
                .title("Weekly Sync")
                .timezone("Asia/Kolkata")
                .startDate(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .recurrenceRule(RecurrenceRuleDto.builder()
                        .frequency(RecurrenceFrequency.WEEKLY)
                        .intervalValue(1)
                        .weekdays(Set.of(DayOfWeek.MONDAY))
                        .build())
                .build();

        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));
        when(timezoneService.parseZoneId("Asia/Kolkata")).thenReturn(ZoneId.of("Asia/Kolkata"));

        List<OccurrenceProposal> proposals = List.of(
                new OccurrenceProposal(LocalDate.of(2026, 1, 5), Instant.parse("2026-01-05T04:30:00Z"), Instant.parse("2026-01-05T05:30:00Z")),
                new OccurrenceProposal(LocalDate.of(2026, 1, 12), Instant.parse("2026-01-12T04:30:00Z"), Instant.parse("2026-01-12T05:30:00Z"))
        );
        when(recurrenceService.generateOccurrences(any(MeetingSeries.class), any(), any()))
                .thenReturn(proposals);

        // Simulate conflict on Jan 12
        ConflictDetail conflict = ConflictDetail.builder()
                .conflictingOccurrenceId(999L)
                .requestedLocalDate(LocalDate.of(2026, 1, 12))
                .build();
        when(conflictDetectionService.batchCheckConflicts(eq(10L), eq(proposals), isNull()))
                .thenReturn(ConflictDetectionResult.withConflicts(List.of(conflict)));

        RoomConflictException ex = assertThrows(RoomConflictException.class, () ->
                meetingService.createMeeting(request, 1L)
        );

        assertEquals(1, ex.getConflicts().size());
        assertEquals(LocalDate.of(2026, 1, 12), ex.getConflicts().get(0).requestedLocalDate());
        // Verify database was NOT written
        verify(seriesRepository, never()).save(any());
    }

    @Test
    @DisplayName("Editing THIS occurrence: modifies single occurrence and marks isException=true")
    void testEditThisOccurrence() {
        MeetingSeries series = MeetingSeries.builder()
                .id(100L)
                .room(room)
                .organizer(organizer)
                .title("Standup")
                .timezone("Asia/Kolkata")
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(SeriesStatus.ACTIVE)
                .build();

        MeetingOccurrence occurrence = MeetingOccurrence.builder()
                .id(50L)
                .series(series)
                .room(room)
                .originalLocalDate(LocalDate.of(2026, 9, 25))
                .startTimeUtc(Instant.parse("2026-09-25T04:30:00Z"))
                .endTimeUtc(Instant.parse("2026-09-25T05:00:00Z"))
                .status(OccurrenceStatus.CONFIRMED)
                .isException(false)
                .build();

        when(occurrenceRepository.findById(50L)).thenReturn(Optional.of(occurrence));
        when(timezoneService.parseZoneId("Asia/Kolkata")).thenReturn(ZoneId.of("Asia/Kolkata"));

        // Move from 10:00 to 14:00 (2 PM)
        EditOccurrenceRequest editRequest = EditOccurrenceRequest.builder()
                .mode(EditMode.THIS)
                .newStartTime(LocalTime.of(14, 0))
                .newEndTime(LocalTime.of(14, 30))
                .build();

        Instant updatedStartUtc = Instant.parse("2026-09-25T08:30:00Z");
        Instant updatedEndUtc = Instant.parse("2026-09-25T09:00:00Z");

        when(timezoneService.toUtcInstant(eq(LocalDate.of(2026, 9, 25)), eq(LocalTime.of(14, 0)), any())).thenReturn(updatedStartUtc);
        when(timezoneService.toUtcInstant(eq(LocalDate.of(2026, 9, 25)), eq(LocalTime.of(14, 30)), any())).thenReturn(updatedEndUtc);

        when(conflictDetectionService.checkSingleConflict(eq(10L), eq(updatedStartUtc), eq(updatedEndUtc), eq(50L)))
                .thenReturn(ConflictDetectionResult.noConflict());

        when(occurrenceRepository.save(any(MeetingOccurrence.class))).thenAnswer(inv -> inv.getArgument(0));

        MeetingOccurrenceResponse response = meetingService.editOccurrence(100L, 50L, editRequest, 1L, false);

        assertNotNull(response);
        assertTrue(response.isException(), "Occurrence must be marked as an exception");
        assertEquals(updatedStartUtc, response.getStartTimeUtc());
    }

    @Test
    @DisplayName("Editing THIS_AND_FUTURE: splits series and preserves past occurrences in old series")
    void testEditThisAndFutureSplitsSeries() {
        LocalDate splitDate = LocalDate.of(2026, 6, 15);

        MeetingSeries oldSeries = MeetingSeries.builder()
                .id(100L)
                .room(room)
                .organizer(organizer)
                .title("Monday Sync")
                .timezone("Asia/Kolkata")
                .startDate(LocalDate.of(2026, 1, 5))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .status(SeriesStatus.ACTIVE)
                .horizonEnd(LocalDate.of(2027, 1, 5))
                .build();

        MeetingOccurrence occurrence = MeetingOccurrence.builder()
                .id(50L)
                .series(oldSeries)
                .room(room)
                .originalLocalDate(splitDate)
                .startTimeUtc(Instant.parse("2026-06-15T04:30:00Z"))
                .endTimeUtc(Instant.parse("2026-06-15T05:30:00Z"))
                .status(OccurrenceStatus.CONFIRMED)
                .build();

        when(occurrenceRepository.findById(50L)).thenReturn(Optional.of(occurrence));

        // Future occurrences in old series
        MeetingOccurrence fut1 = MeetingOccurrence.builder().id(50L).status(OccurrenceStatus.CONFIRMED).build();
        MeetingOccurrence fut2 = MeetingOccurrence.builder().id(51L).status(OccurrenceStatus.CONFIRMED).build();
        when(occurrenceRepository.findFutureOccurrencesFromDate(100L, splitDate))
                .thenReturn(List.of(fut1, fut2));

        EditOccurrenceRequest editRequest = EditOccurrenceRequest.builder()
                .mode(EditMode.THIS_AND_FUTURE)
                .newStartTime(LocalTime.of(15, 0))
                .newEndTime(LocalTime.of(16, 0))
                .build();

        List<OccurrenceProposal> newProposals = List.of(
                new OccurrenceProposal(splitDate, Instant.parse("2026-06-15T09:30:00Z"), Instant.parse("2026-06-15T10:30:00Z"))
        );
        when(recurrenceService.generateOccurrences(any(MeetingSeries.class), eq(splitDate), any()))
                .thenReturn(newProposals);

        when(conflictDetectionService.batchCheckConflicts(eq(10L), eq(newProposals), eq(100L)))
                .thenReturn(ConflictDetectionResult.noConflict());

        when(seriesRepository.save(any(MeetingSeries.class))).thenAnswer(inv -> {
            MeetingSeries s = inv.getArgument(0);
            if (s.getId() == null) s.setId(200L);
            return s;
        });

        MeetingOccurrenceResponse response = meetingService.editOccurrence(100L, 50L, editRequest, 1L, false);

        assertNotNull(response);
        // Verify future occurrences in old series were cancelled
        assertEquals(OccurrenceStatus.CANCELLED, fut1.getStatus());
        assertEquals(OccurrenceStatus.CANCELLED, fut2.getStatus());
        // Verify old series end date was truncated to day before splitDate
        assertEquals(LocalDate.of(2026, 6, 14), oldSeries.getEndDate());
    }

    @Test
    @DisplayName("Should prevent non-organizer non-admin user from modifying meeting")
    void testUnauthorizedUserCannotModifyMeeting() {
        MeetingSeries series = MeetingSeries.builder()
                .id(100L)
                .organizer(organizer) // id = 1
                .room(room)
                .status(SeriesStatus.ACTIVE)
                .build();

        MeetingOccurrence occurrence = MeetingOccurrence.builder()
                .id(50L)
                .series(series)
                .room(room)
                .build();

        when(occurrenceRepository.findById(50L)).thenReturn(Optional.of(occurrence));

        EditOccurrenceRequest editRequest = EditOccurrenceRequest.builder()
                .mode(EditMode.THIS)
                .build();

        // Calling with user id 999 (not organizer, not admin)
        assertThrows(AccessDeniedException.class, () ->
                meetingService.editOccurrence(100L, 50L, editRequest, 999L, false)
        );
    }

    @Test
    @DisplayName("Cancellation: ONE cancels only that occurrence")
    void testCancelOneOccurrence() {
        MeetingSeries series = MeetingSeries.builder().id(100L).organizer(organizer).room(room).build();
        MeetingOccurrence occurrence = MeetingOccurrence.builder().id(50L).series(series).status(OccurrenceStatus.CONFIRMED).build();

        when(seriesRepository.findById(100L)).thenReturn(Optional.of(series));
        when(occurrenceRepository.findById(50L)).thenReturn(Optional.of(occurrence));

        meetingService.cancelMeeting(100L, 50L, CancelMode.ONE, 1L, false);

        assertEquals(OccurrenceStatus.CANCELLED, occurrence.getStatus());
        verify(occurrenceRepository).save(occurrence);
    }

    @Test
    @DisplayName("Availability query reports overlapping confirmed time intervals")
    void availabilityQueryReportsBusyIntervals() {
        Instant from = Instant.parse("2026-09-25T10:00:00Z");
        Instant to = Instant.parse("2026-09-25T11:00:00Z");
        MeetingSeries series = MeetingSeries.builder().id(40L).organizer(organizer).title("private title").build();
        MeetingOccurrence busy = MeetingOccurrence.builder().id(70L).series(series).room(room)
                .startTimeUtc(Instant.parse("2026-09-25T10:30:00Z"))
                .endTimeUtc(Instant.parse("2026-09-25T11:30:00Z")).build();
        when(roomRepository.existsById(10L)).thenReturn(true);
        when(occurrenceRepository.findConflictingOccurrences(10L, from, to, null)).thenReturn(List.of(busy));

        RoomAvailabilityResponse response = meetingService.getRoomAvailability(10L, from, to);

        assertFalse(response.isAvailable());
        assertEquals(1, response.getConflictingIntervals().size());
        assertEquals(Instant.parse("2026-09-25T10:30:00Z"), response.getConflictingIntervals().get(0).getStart());
    }

    @Test
    @DisplayName("A user cannot read another organizer's meeting series")
    void unauthorizedUserCannotReadMeeting() {
        MeetingSeries series = MeetingSeries.builder().id(100L).organizer(organizer).room(room).build();
        when(seriesRepository.findById(100L)).thenReturn(Optional.of(series));
        assertThrows(AccessDeniedException.class, () -> meetingService.getMeetingById(100L, 999L, false));
    }

    @Test
    @DisplayName("Occurrence cancellation rejects an occurrence from a different series")
    void cancellationRejectsForeignOccurrence() {
        MeetingSeries ownedSeries = MeetingSeries.builder().id(100L).organizer(organizer).room(room).build();
        MeetingSeries foreignSeries = MeetingSeries.builder().id(200L).organizer(organizer).room(room).build();
        MeetingOccurrence occurrence = MeetingOccurrence.builder().id(50L).series(foreignSeries).build();
        when(seriesRepository.findById(100L)).thenReturn(Optional.of(ownedSeries));
        when(occurrenceRepository.findById(50L)).thenReturn(Optional.of(occurrence));
        assertThrows(ResourceNotFoundException.class,
                () -> meetingService.cancelMeeting(100L, 50L, CancelMode.ONE, 1L, false));
        verify(occurrenceRepository, never()).save(occurrence);
    }

    @Test
    @DisplayName("Whole-series metadata edits reuse unchanged future reservations")
    void wholeSeriesMetadataEditDoesNotDuplicateExistingOccurrence() {
        LocalDate date = LocalDate.now().plusDays(2);
        Instant start = Instant.now().plusSeconds(172800);
        MeetingSeries series = MeetingSeries.builder().id(100L).room(room).organizer(organizer)
                .title("Old title").timezone("Asia/Kolkata").startDate(date.minusDays(10))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .horizonEnd(date.plusMonths(12)).status(SeriesStatus.ACTIVE)
                .recurrenceRule(RecurrenceRule.builder().frequency(RecurrenceFrequency.DAILY).intervalValue(1).build())
                .build();
        MeetingOccurrence existing = MeetingOccurrence.builder().id(88L).series(series).room(room)
                .startTimeUtc(start).endTimeUtc(start.plusSeconds(3600)).originalLocalDate(date)
                .status(OccurrenceStatus.CONFIRMED).build();
        series.setOccurrences(new ArrayList<>(List.of(existing)));
        when(occurrenceRepository.findById(88L)).thenReturn(Optional.of(existing));
        when(recurrenceService.generateOccurrences(eq(series), any(), any()))
                .thenReturn(List.of(new OccurrenceProposal(date, start, start.plusSeconds(3600))));
        when(conflictDetectionService.batchCheckConflicts(eq(10L), anyList(), eq(100L)))
                .thenReturn(ConflictDetectionResult.noConflict());
        when(seriesRepository.save(series)).thenReturn(series);

        MeetingOccurrenceResponse response = meetingService.editOccurrence(100L, 88L,
                EditOccurrenceRequest.builder().mode(EditMode.WHOLE_SERIES).newTitle("New title").build(), 1L, false);

        assertEquals("New title", response.getTitle());
        assertEquals(1, series.getOccurrences().size());
        assertEquals(OccurrenceStatus.CONFIRMED, existing.getStatus());
        verify(occurrenceRepository).flush();
    }
}
