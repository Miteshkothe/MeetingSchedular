package com.meetingbooking.recurrence;

import com.meetingbooking.entity.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceDstIntegrationUnitTest {
    @Test
    void recurringLocalTimeStaysConstantAcrossSpringForwardWhileUtcChanges() {
        RecurrenceService service = new RecurrenceService(List.of(new DailyRecurrenceGenerator(),
                new WeeklyRecurrenceGenerator(), new MonthlyRecurrenceGenerator()), new TimezoneService(),
                new SimpleMeterRegistry());
        RecurrenceRule rule = RecurrenceRule.builder().frequency(RecurrenceFrequency.DAILY)
                .intervalValue(1).endDate(LocalDate.of(2026, 3, 9)).build();
        MeetingSeries series = MeetingSeries.builder().startDate(LocalDate.of(2026, 3, 7))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .timezone("America/New_York").recurrenceRule(rule).build();

        List<OccurrenceProposal> occurrences = service.generateOccurrences(series,
                LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 9));

        assertEquals(3, occurrences.size());
        assertEquals(Instant.parse("2026-03-07T14:00:00Z"), occurrences.get(0).startTimeUtc());
        assertEquals(Instant.parse("2026-03-08T13:00:00Z"), occurrences.get(1).startTimeUtc());
        assertEquals(LocalTime.of(9, 0), LocalDateTime.ofInstant(occurrences.get(1).startTimeUtc(), ZoneId.of("America/New_York")).toLocalTime());
    }
}
