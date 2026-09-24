package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import com.meetingbooking.exception.InvalidRecurrenceRuleException;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Component
public class MonthlyRecurrenceGenerator implements RecurrenceGenerator {

    @Override
    public RecurrenceFrequency getFrequency() {
        return RecurrenceFrequency.MONTHLY;
    }

    @Override
    public List<LocalDate> generateDates(LocalDate seriesStartDate, LocalDate windowStart, LocalDate windowEnd, RecurrenceRule rule) {
        List<LocalDate> dates = new ArrayList<>();
        int intervalMonths = (rule.getIntervalValue() != null && rule.getIntervalValue() >= 1) ? rule.getIntervalValue() : 1;

        LocalDate effectiveEnd = windowEnd;
        if (rule.getEndDate() != null && rule.getEndDate().isBefore(effectiveEnd)) {
            effectiveEnd = rule.getEndDate();
        }

        boolean isFixedDay = rule.getDayOfMonth() != null;
        boolean isNthWeekday = rule.getWeekNumber() != null && rule.getWeekday() != null && !rule.getWeekday().isBlank();

        if (!isFixedDay && !isNthWeekday) {
            throw new InvalidRecurrenceRuleException("Monthly recurrence requires either dayOfMonth or weekNumber + weekday.");
        }

        YearMonth currentYm = YearMonth.from(seriesStartDate);
        YearMonth maxYm = YearMonth.from(effectiveEnd);

        int count = 0;
        Integer maxCount = rule.getOccurrenceCount();

        while (!currentYm.isAfter(maxYm)) {
            LocalDate candidate;

            if (isFixedDay) {
                // Fixed day of month (e.g. 15th or 31st).
                // Policy: Clamp to the last valid day of month if the requested day exceeds the month length.
                int requestedDay = rule.getDayOfMonth();
                int maxDayInMonth = currentYm.lengthOfMonth();
                int actualDay = Math.min(requestedDay, maxDayInMonth);
                candidate = currentYm.atDay(actualDay);
            } else {
                // Nth weekday of month (e.g. 2nd Wednesday).
                DayOfWeek targetDow = DayOfWeek.valueOf(rule.getWeekday().trim().toUpperCase());
                int weekNumber = rule.getWeekNumber();

                if (weekNumber == -1) {
                    // Last matching weekday of month
                    candidate = currentYm.atEndOfMonth().with(TemporalAdjusters.previousOrSame(targetDow));
                } else {
                    // 1st, 2nd, 3rd, 4th, 5th
                    LocalDate firstOccurrence = currentYm.atDay(1).with(TemporalAdjusters.nextOrSame(targetDow));
                    LocalDate nthOccurrence = firstOccurrence.plusWeeks(weekNumber - 1);

                    // If 5th occurrence spills into next month, policy: clamp to the last matching weekday of the month
                    if (nthOccurrence.getMonth() != currentYm.getMonth()) {
                        candidate = currentYm.atEndOfMonth().with(TemporalAdjusters.previousOrSame(targetDow));
                    } else {
                        candidate = nthOccurrence;
                    }
                }
            }

            if (!candidate.isBefore(seriesStartDate) && !candidate.isAfter(effectiveEnd)) {
                count++;
                if (maxCount != null && count > maxCount) {
                    break;
                }

                if (!candidate.isBefore(windowStart)) {
                    dates.add(candidate);
                }
            }

            currentYm = currentYm.plusMonths(intervalMonths);
        }

        return dates;
    }
}
