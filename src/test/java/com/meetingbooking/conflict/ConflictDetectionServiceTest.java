package com.meetingbooking.conflict;

import com.meetingbooking.entity.MeetingOccurrence;
import com.meetingbooking.entity.MeetingSeries;
import com.meetingbooking.entity.Room;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.repository.MeetingOccurrenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    @Mock
    private MeetingOccurrenceRepository occurrenceRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private ConflictDetectionService conflictService;

    @BeforeEach
    void setUp() {
        conflictService = new ConflictDetectionService(occurrenceRepository, jdbcTemplate);
    }

    @Test
    @DisplayName("Edge Case 1: Meeting starts exactly when another ends -> NO conflict")
    void testBackToBackMeetingsNoConflict() {
        Instant m1Start = Instant.parse("2026-09-23T10:00:00Z");
        Instant m1End = Instant.parse("2026-09-23T11:00:00Z");

        Instant m2Start = Instant.parse("2026-09-23T11:00:00Z");
        Instant m2End = Instant.parse("2026-09-23T12:00:00Z");

        boolean overlap1 = conflictService.intervalsOverlap(m1Start, m1End, m2Start, m2End);
        boolean overlap2 = conflictService.intervalsOverlap(m2Start, m2End, m1Start, m1End);

        assertFalse(overlap1, "Back-to-back meetings must not conflict");
        assertFalse(overlap2, "Back-to-back meetings must not conflict in reverse");
    }

    @Test
    @DisplayName("Edge Case 2: Meeting starts 1 second before another ends -> CONFLICT")
    void testOneSecondOverlapConflict() {
        Instant m1Start = Instant.parse("2026-09-23T10:00:00Z");
        Instant m1End = Instant.parse("2026-09-23T11:00:00Z");

        Instant m2Start = Instant.parse("2026-09-23T10:59:59Z");
        Instant m2End = Instant.parse("2026-09-23T12:00:00Z");

        boolean overlap = conflictService.intervalsOverlap(m1Start, m1End, m2Start, m2End);

        assertTrue(overlap, "A 1-second overlap must register as a conflict");
    }

    @Test
    @DisplayName("Interval overlap: Enclosed interval -> CONFLICT")
    void testEnclosedIntervalConflict() {
        Instant outerStart = Instant.parse("2026-09-23T10:00:00Z");
        Instant outerEnd = Instant.parse("2026-09-23T12:00:00Z");

        Instant innerStart = Instant.parse("2026-09-23T10:30:00Z");
        Instant innerEnd = Instant.parse("2026-09-23T11:30:00Z");

        assertTrue(conflictService.intervalsOverlap(outerStart, outerEnd, innerStart, innerEnd));
        assertTrue(conflictService.intervalsOverlap(innerStart, innerEnd, outerStart, outerEnd));
    }

    @Test
    @DisplayName("Batch conflict validation: Detects conflict halfway through recurring series")
    void testBatchConflictDetectionHalfway() {
        Long roomId = 1L;
        LocalDate d1 = LocalDate.of(2026, 1, 5);
        LocalDate d2 = LocalDate.of(2026, 1, 12); // Conflicting date
        LocalDate d3 = LocalDate.of(2026, 1, 19);

        List<OccurrenceProposal> proposals = List.of(
                new OccurrenceProposal(d1, Instant.parse("2026-01-05T10:00:00Z"), Instant.parse("2026-01-05T11:00:00Z")),
                new OccurrenceProposal(d2, Instant.parse("2026-01-12T10:00:00Z"), Instant.parse("2026-01-12T11:00:00Z")),
                new OccurrenceProposal(d3, Instant.parse("2026-01-19T10:00:00Z"), Instant.parse("2026-01-19T11:00:00Z"))
        );

        Room room = Room.builder().id(roomId).name("Conference Alpha").build();
        MeetingSeries existingSeries = MeetingSeries.builder().id(99L).title("Existing Weekly Sync").build();

        MeetingOccurrence conflictingBooking = MeetingOccurrence.builder()
                .id(501L)
                .room(room)
                .series(existingSeries)
                .startTimeUtc(Instant.parse("2026-01-12T10:30:00Z"))
                .endTimeUtc(Instant.parse("2026-01-12T11:30:00Z"))
                .originalLocalDate(d2)
                .build();

        ConflictDetail dbConflict = ConflictDetail.builder()
                .requestedStart(Instant.parse("2026-01-12T10:00:00Z"))
                .requestedEnd(Instant.parse("2026-01-12T11:00:00Z"))
                .requestedLocalDate(d2).conflictingOccurrenceId(501L).conflictingSeriesId(99L)
                .conflictingTitle("Existing Weekly Sync")
                .conflictingStart(conflictingBooking.getStartTimeUtc()).conflictingEnd(conflictingBooking.getEndTimeUtc())
                .build();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ConflictDetail>>any(), any(Object[].class)))
                .thenReturn(List.of(dbConflict));

        ConflictDetectionResult result = conflictService.batchCheckConflicts(roomId, proposals, null);

        assertTrue(result.hasConflict(), "Batch check should detect conflict on Jan 12");
        assertEquals(1, result.conflicts().size());
        ConflictDetail conflict = result.conflicts().get(0);
        assertEquals(d2, conflict.requestedLocalDate());
        assertEquals(501L, conflict.conflictingOccurrenceId());
        assertEquals("Existing Weekly Sync", conflict.conflictingTitle());
    }

    @Test
    @DisplayName("Batch conflict validation: No conflicts when room is free")
    void testBatchConflictDetectionNoConflict() {
        Long roomId = 1L;
        List<OccurrenceProposal> proposals = List.of(
                new OccurrenceProposal(LocalDate.of(2026, 1, 5), Instant.parse("2026-01-05T10:00:00Z"), Instant.parse("2026-01-05T11:00:00Z"))
        );

        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<ConflictDetail>>any(), any(Object[].class)))
                .thenReturn(List.of());

        ConflictDetectionResult result = conflictService.batchCheckConflicts(roomId, proposals, null);

        assertFalse(result.hasConflict());
        assertTrue(result.conflicts().isEmpty());
    }
}
