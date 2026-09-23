package com.meetingbooking.dto;

import com.meetingbooking.entity.SeriesStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSeriesResponse {
    private Long id;
    private Long roomId;
    private String roomName;
    private Long organizerId;
    private String organizerName;
    private String title;
    private String description;
    private String timezone;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private RecurrenceRuleDto recurrenceRule;
    private SeriesStatus status;
    private LocalDate horizonEnd;
    private List<AttendeeDto> attendees;
    private List<MeetingOccurrenceResponse> occurrences;
    private Instant createdAt;
    private Instant updatedAt;
}
