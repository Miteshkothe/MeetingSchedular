package com.meetingbooking.dto;

import com.meetingbooking.entity.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeDto {
    private Long userId;
    private String name;
    private String email;
    private AttendanceStatus status;
}
