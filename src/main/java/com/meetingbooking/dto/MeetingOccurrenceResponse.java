package com.meetingbooking.dto;

import com.meetingbooking.entity.OccurrenceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingOccurrenceResponse {
    private Long id;
    private Long seriesId;
    private String title;
    private String description;
    private Long roomId;
    private String roomName;
    private Long organizerId;
    private String organizerName;
    private String timezone;
    private Instant startTimeUtc;
    private Instant endTimeUtc;
    private LocalDate originalLocalDate;
    private OccurrenceStatus status;
    private boolean isException;
    private List<AttendeeDto> attendees;
    private Instant createdAt;
    private Instant updatedAt;
}
