package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DailyRecurrenceGenerator implements RecurrenceGenerator {

    @Override
    public RecurrenceFrequency getFrequency() {
        return RecurrenceFrequency.DAILY;
    }

    @Override
    public List<LocalDate> generateDates(LocalDate seriesStartDate, LocalDate windowStart, LocalDate windowEnd, RecurrenceRule rule) {
        List<LocalDate> dates = new ArrayList<>();
        int interval = (rule.getIntervalValue() != null && rule.getIntervalValue() >= 1) ? rule.getIntervalValue() : 1;
        LocalDate effectiveEnd = windowEnd;
        if (rule.getEndDate() != null && rule.getEndDate().isBefore(effectiveEnd)) {
            effectiveEnd = rule.getEndDate();
        }

        LocalDate current = seriesStartDate;
        int count = 0;
        Integer maxCount = rule.getOccurrenceCount();

        while (!current.isAfter(effectiveEnd)) {
            count++;
            if (maxCount != null && count > maxCount) {
                break;
            }

            if (!current.isBefore(windowStart)) {
                dates.add(current);
            }

            current = current.plusDays(interval);
        }

        return dates;
    }
}
