package com.meetingbooking.exception;

import com.meetingbooking.conflict.ConflictDetail;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class RoomConflictException extends RuntimeException {

    private final List<ConflictDetail> conflicts;

    public RoomConflictException(String message) {
        super(message);
        this.conflicts = Collections.emptyList();
    }

    public RoomConflictException(String message, List<ConflictDetail> conflicts) {
        super(message);
        this.conflicts = conflicts != null ? conflicts : Collections.emptyList();
    }
}
