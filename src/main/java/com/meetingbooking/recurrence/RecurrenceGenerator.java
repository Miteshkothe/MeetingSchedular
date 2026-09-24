package com.meetingbooking.recurrence;

import com.meetingbooking.entity.RecurrenceFrequency;
import com.meetingbooking.entity.RecurrenceRule;

import java.time.LocalDate;
import java.util.List;

public interface RecurrenceGenerator {

    RecurrenceFrequency getFrequency();
    List<LocalDate> generateDates(LocalDate seriesStartDate, LocalDate windowStart, LocalDate windowEnd, RecurrenceRule rule);
}
