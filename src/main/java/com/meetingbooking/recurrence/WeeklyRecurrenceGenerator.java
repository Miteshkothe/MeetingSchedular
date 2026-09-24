package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class WeeklyRecurrenceGenerator implements RecurrenceGenerator {

    @Override
    public RecurrenceFrequency getFrequency() {
        return RecurrenceFrequency.WEEKLY;
    }

    @Override
    public List<LocalDate> generateDates(LocalDate seriesStartDate, LocalDate windowStart, LocalDate windowEnd, RecurrenceRule rule) {
        List<LocalDate> dates = new ArrayList<>();
        int intervalWeeks = (rule.getIntervalValue() != null && rule.getIntervalValue() >= 1) ? rule.getIntervalValue() : 1;

        LocalDate effectiveEnd = windowEnd;
        if (rule.getEndDate() != null && rule.getEndDate().isBefore(effectiveEnd)) {
            effectiveEnd = rule.getEndDate();
        }

        Set<DayOfWeek> targetDays = rule.getWeekdaysSet();
        if (targetDays.isEmpty()) {
            targetDays = Set.of(seriesStartDate.getDayOfWeek());
        }

        List<DayOfWeek> sortedDays = targetDays.stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList();

        LocalDate currentWeekMonday = seriesStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int count = 0;
        Integer maxCount = rule.getOccurrenceCount();

        while (!currentWeekMonday.isAfter(effectiveEnd)) {
            for (DayOfWeek day : sortedDays) {

                LocalDate candidate = currentWeekMonday.plusDays((long) day.getValue() - 1);

                if (candidate.isBefore(seriesStartDate)) {
                    continue;
                }

                if (candidate.isAfter(effectiveEnd)) {
                    break;
                }

                count++;
                if (maxCount != null && count > maxCount) {
                    return dates;
                }

                if (!candidate.isBefore(windowStart)) {
                    dates.add(candidate);
                }
            }

            currentWeekMonday = currentWeekMonday.plusWeeks(intervalWeeks);
        }

        return dates;
    }
}
