package com.meetingbooking.conflict;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Builder
public record ConflictDetail(
        Long conflictingOccurrenceId,
        Long conflictingSeriesId,
        String conflictingTitle,
        Instant conflictingStart,
        Instant conflictingEnd,
        Instant requestedStart,
        Instant requestedEnd,
        LocalDate requestedLocalDate
) {}
