package com.meetingbooking.recurrence;

import java.time.Instant;
import java.time.LocalDate;

public record OccurrenceProposal(
        LocalDate originalLocalDate,
        Instant startTimeUtc,
        Instant endTimeUtc
) {}
