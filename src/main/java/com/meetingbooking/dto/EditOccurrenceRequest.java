package com.meetingbooking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditOccurrenceRequest {

    @NotNull(message = "Edit mode is required (THIS, THIS_AND_FUTURE, WHOLE_SERIES)")
    private EditMode mode;

    private Long newRoomId;

    private String newTitle;

    private String newDescription;

    private LocalTime newStartTime;

    private LocalTime newEndTime;

    /**
     * For mode THIS: allows moving this specific occurrence to another date.
     */
    private LocalDate newLocalDate;

    /**
     * For mode THIS_AND_FUTURE or WHOLE_SERIES: allows changing the recurring rule.
     */
    private RecurrenceRuleDto newRecurrenceRule;
}
