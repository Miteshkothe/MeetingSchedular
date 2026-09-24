package com.meetingbooking.conflict;

import java.util.List;

public record ConflictDetectionResult(
        boolean hasConflict,
        List<ConflictDetail> conflicts
) {
    public static ConflictDetectionResult noConflict() {
        return new ConflictDetectionResult(false, List.of());
    }

    public static ConflictDetectionResult withConflicts(List<ConflictDetail> conflicts) {
        return new ConflictDetectionResult(!conflicts.isEmpty(), conflicts);
    }
}
