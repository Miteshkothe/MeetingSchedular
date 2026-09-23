package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyRecurrenceGeneratorTest {

    private MonthlyRecurrenceGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MonthlyRecurrenceGenerator();
    }

    @Test
    @DisplayName("Should generate fixed day of month (15th)")
    void testMonthlyFixedDay() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        LocalDate windowEnd = LocalDate.of(2026, 4, 30);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .intervalValue(1)
                .dayOfMonth(15)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(4, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 15)
        ), dates);
    }

    @Test
    @DisplayName("Should handle 31st policy on 30-day months and February (non-leap year 2023)")
    void testMonthly31stNonLeapYear() {
        // 2023: Jan has 31, Feb has 28, Mar has 31, Apr has 30
        LocalDate start = LocalDate.of(2023, 1, 31);
        LocalDate windowEnd = LocalDate.of(2023, 4, 30);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .intervalValue(1)
                .dayOfMonth(31)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(4, dates.size());
        assertEquals(List.of(
                LocalDate.of(2023, 1, 31),
                LocalDate.of(2023, 2, 28), // Clamped to Feb 28
                LocalDate.of(2023, 3, 31),
                LocalDate.of(2023, 4, 30)  // Clamped to April 30
        ), dates);
    }

    @Test
    @DisplayName("Should handle February 29 on leap year (2024)")
    void testMonthly31stLeapYear() {
        // 2024 is a leap year: Feb has 29 days
        LocalDate start = LocalDate.of(2024, 1, 31);
        LocalDate windowEnd = LocalDate.of(2024, 2, 29);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .intervalValue(1)
                .dayOfMonth(31)
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(2, dates.size());
        assertEquals(List.of(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29) // Clamped to Feb 29 on leap year!
        ), dates);
    }

    @Test
    @DisplayName("Should generate 2nd Wednesday of every month")
    void testMonthly2ndWednesday() {
        // Jan 2026: 1st is Thursday. 1st Wed = Jan 7, 2nd Wed = Jan 14.
        // Feb 2026: 1st is Sunday. 1st Wed = Feb 4, 2nd Wed = Feb 11.
        // Mar 2026: 1st is Sunday. 1st Wed = Mar 4, 2nd Wed = Mar 11.
        LocalDate start = LocalDate.of(2026, 1, 14);
        LocalDate windowEnd = LocalDate.of(2026, 3, 31);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .intervalValue(1)
                .weekNumber(2)
                .weekday("WEDNESDAY")
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(3, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 14),
                LocalDate.of(2026, 2, 11),
                LocalDate.of(2026, 3, 11)
        ), dates);
    }

    @Test
    @DisplayName("Should generate last Friday of every month (-1)")
    void testMonthlyLastFriday() {
        // Jan 2026: Last Friday is Jan 30
        // Feb 2026: Last Friday is Feb 27
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate windowEnd = LocalDate.of(2026, 2, 28);

        RecurrenceRule rule = RecurrenceRule.builder()
                .frequency(RecurrenceFrequency.MONTHLY)
                .intervalValue(1)
                .weekNumber(-1)
                .weekday("FRIDAY")
                .build();

        List<LocalDate> dates = generator.generateDates(start, start, windowEnd, rule);

        assertEquals(2, dates.size());
        assertEquals(List.of(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 2, 27)
        ), dates);
    }
}
