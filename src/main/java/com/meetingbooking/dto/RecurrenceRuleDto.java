package com.meetingbooking.dto;

import com.meetingbooking.entity.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRuleDto {

    @NotNull(message = "frequency is required")
    private RecurrenceFrequency frequency;

    @Builder.Default
    @Min(value = 1, message = "intervalValue must be at least 1")
    private Integer intervalValue = 1;

    private Set<DayOfWeek> weekdays;

    @Min(value = 1, message = "dayOfMonth must be between 1 and 31")
    @Max(value = 31, message = "dayOfMonth must be between 1 and 31")
    private Integer dayOfMonth;

    @Min(value = -1, message = "weekNumber must be between -1 and 5")
    @Max(value = 5, message = "weekNumber must be between -1 and 5")
    private Integer weekNumber;

    private String weekday;

    private LocalDate endDate;

    @Min(value = 1, message = "occurrenceCount must be at least 1")
    private Integer occurrenceCount;
}
