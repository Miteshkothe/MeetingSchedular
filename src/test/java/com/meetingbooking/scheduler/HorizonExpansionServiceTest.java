package com.meetingbooking.scheduler;

import com.meetingbooking.conflict.ConflictDetectionResult;
import com.meetingbooking.conflict.ConflictDetectionService;
import com.meetingbooking.entity.*;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.recurrence.RecurrenceService;
import com.meetingbooking.repository.MeetingOccurrenceRepository;
import com.meetingbooking.repository.MeetingSeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HorizonExpansionServiceTest {
    @Test
    void repeatedExpansionDoesNotMaterializeTheSameWindowTwice() {
        MeetingSeriesRepository seriesRepository = mock(MeetingSeriesRepository.class);
        MeetingOccurrenceRepository occurrenceRepository = mock(MeetingOccurrenceRepository.class);
        RecurrenceService recurrenceService = mock(RecurrenceService.class);
        ConflictDetectionService conflicts = mock(ConflictDetectionService.class);
        HorizonExpansionService service = new HorizonExpansionService(seriesRepository, occurrenceRepository, recurrenceService, conflicts);
        ReflectionTestUtils.setField(service, "rollingHorizonMonths", 12);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        MeetingSeries series = MeetingSeries.builder().id(5L).room(Room.builder().id(2L).build())
                .timezone("Asia/Kolkata").startDate(today.minusMonths(2)).startTime(LocalTime.NOON)
                .endTime(LocalTime.of(13, 0)).status(SeriesStatus.ACTIVE)
                .horizonEnd(today.minusDays(1)).recurrenceRule(RecurrenceRule.builder()
                        .frequency(RecurrenceFrequency.DAILY).intervalValue(1).build()).build();
        OccurrenceProposal proposal = new OccurrenceProposal(today, Instant.parse("2026-09-23T06:30:00Z"),
                Instant.parse("2026-09-23T07:30:00Z"));
        when(seriesRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(series));
        when(recurrenceService.generateOccurrences(eq(series), any(), any())).thenReturn(List.of(proposal));
        when(conflicts.batchCheckConflicts(eq(2L), anyList(), eq(5L))).thenReturn(ConflictDetectionResult.noConflict());

        assertTrue(service.expandSeries(5L));
        assertFalse(service.expandSeries(5L));
        verify(recurrenceService, times(1)).generateOccurrences(eq(series), any(), any());
        verify(occurrenceRepository, times(1)).saveAll(anyList());
    }
}
