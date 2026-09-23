package com.meetingbooking.service;

import com.meetingbooking.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface MeetingService {

    MeetingSeriesResponse createMeeting(CreateMeetingRequest request, Long currentUserId);

    MeetingSeriesResponse getMeetingById(Long seriesId, Long userId, boolean isAdmin);

    MeetingOccurrenceResponse getOccurrenceById(Long occurrenceId, Long userId, boolean isAdmin);

    Page<MeetingOccurrenceResponse> getCalendarOccurrences(Instant from, Instant to, Long roomId, Long userId, boolean isAdmin, Pageable pageable);

    RoomAvailabilityResponse getRoomAvailability(Long roomId, Instant from, Instant to);

    MeetingOccurrenceResponse editOccurrence(Long seriesId, Long occurrenceId, EditOccurrenceRequest request, Long currentUserId, boolean isAdmin);

    void cancelMeeting(Long seriesId, Long occurrenceId, CancelMode mode, Long currentUserId, boolean isAdmin);
}
