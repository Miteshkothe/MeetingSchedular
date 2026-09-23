package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyRecurrenceGeneratorTest {

    private WeeklyRecurrenceGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new WeeklyRecurrenceGenerator();
    }

    @Test
    @DisplayName("Should generate single day weekly occurrences")
    void testWeeklySingleDay() {
        // 2026-01-05 is a Monday
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate windowEnd = LocalDate.of(2026, 1, 26);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .intervalValue(1)
                .build();
        rule.setWeekdaysSet(Set.of(DayOfWeek.MONDAY));

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(4, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 1, 26)
        ), dates);
    }

    @Test
    @DisplayName("Should generate multiple weekdays (Monday, Wednesday, Friday)")
    void testWeeklyMultipleWeekdays() {
        // 2026-01-05 is a Monday
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate windowEnd = LocalDate.of(2026, 1, 11); // 1 full week

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .intervalValue(1)
                .build();
        rule.setWeekdaysSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(3, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 7),
                LocalDate.of(2026, 1, 9)
        ), dates);
    }

    @Test
    @DisplayName("Should generate bi-weekly recurrence (every 2 weeks on Monday and Wednesday)")
    void testBiWeeklyEvery2Weeks() {
        // 2026-01-05 is Monday of Week 1
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate windowEnd = LocalDate.of(2026, 1, 25); // 3 weeks

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .intervalValue(2)
                .build();
        rule.setWeekdaysSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        // Week 1: Mon Jan 5, Wed Jan 7
        // Week 2: Skipped (interval 2)
        // Week 3: Mon Jan 19, Wed Jan 21
        assertEquals(4, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 7),
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 1, 21)
        ), dates);
    }

    @Test
    @DisplayName("Should not include weekdays prior to start date in week 1")
    void testSeriesStartingMidWeek() {
        // 2026-01-07 is Wednesday
        LocalDate start = LocalDate.of(2026, 1, 7);
        LocalDate windowEnd = LocalDate.of(2026, 1, 14);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.WEEKLY)
                .intervalValue(1)
                .build();
        rule.setWeekdaysSet(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        // Monday Jan 5 must NOT be included because series started Jan 7
        assertEquals(List.of(
                LocalDate.of(2026, 1, 7),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 14)
        ), dates);
    }
}
