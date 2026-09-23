package com.meetingbooking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelMeetingRequest {

    @NotNull(message = "Cancel mode is required (ONE, THIS_AND_FUTURE, WHOLE_SERIES)")
    private CancelMode mode;
}
