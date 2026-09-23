package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyRecurrenceGeneratorTest {

    private DailyRecurrenceGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DailyRecurrenceGenerator();
    }

    @Test
    @DisplayName("Should generate consecutive daily occurrences")
    void testDailyEveryDay() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate windowEnd = LocalDate.of(2026, 1, 5);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .intervalValue(1)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(5, dates.size());
        assertEquals(LocalDate.of(2026, 1, 1), dates.get(0));
        assertEquals(LocalDate.of(2026, 1, 2), dates.get(1));
        assertEquals(LocalDate.of(2026, 1, 3), dates.get(2));
        assertEquals(LocalDate.of(2026, 1, 4), dates.get(3));
        assertEquals(LocalDate.of(2026, 1, 5), dates.get(4));
    }

    @Test
    @DisplayName("Should generate daily occurrences every 3 days")
    void testDailyEvery3Days() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate windowEnd = LocalDate.of(2026, 1, 10);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .intervalValue(3)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(4, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 7),
                LocalDate.of(2026, 1, 10)
        ), dates);
    }

    @Test
    @DisplayName("Should respect occurrence count limit")
    void testDailyWithOccurrenceCount() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate windowEnd = LocalDate.of(2026, 1, 31);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .intervalValue(1)
                .occurrenceCount(3)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(3, dates.size());
    }

    @Test
    @DisplayName("Should respect rule end date")
    void testDailyWithEndDate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate windowEnd = LocalDate.of(2026, 1, 31);
        LocalDate ruleEnd = LocalDate.of(2026, 1, 4);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.DAILY)
                .intervalValue(1)
                .endDate(ruleEnd)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(4, dates.size());
        assertEquals(LocalDate.of(2026, 1, 4), dates.get(dates.size() - 1));
    }
}
