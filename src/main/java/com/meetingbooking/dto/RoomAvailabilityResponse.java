package com.meetingbooking.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class RoomAvailabilityResponse {
    Long roomId;
    Instant requestedStart;
    Instant requestedEnd;
    boolean available;
    List<BusyInterval> conflictingIntervals;

    @Value
    @Builder
    public static class BusyInterval {
        Instant start;
        Instant end;
    }
}
