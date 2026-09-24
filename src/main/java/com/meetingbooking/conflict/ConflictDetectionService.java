package com.meetingbooking.conflict;

import com.meetingbooking.entity.MeetingOccurrence;
import com.meetingbooking.recurrence.OccurrenceProposal;
import com.meetingbooking.repository.MeetingOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictDetectionService {

    private final MeetingOccurrenceRepository occurrenceRepository;
    private final JdbcTemplate jdbcTemplate;
    public boolean intervalsOverlap(Instant start1, Instant end1, Instant start2, Instant end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }
    @Transactional(readOnly = true)
    public ConflictDetectionResult checkSingleConflict(
            Long roomId,
            Instant startUtc,
            Instant endUtc,
            Long excludeOccurrenceId
    ) {
        List<MeetingOccurrence> conflicting = occurrenceRepository.findConflictingOccurrences(
                roomId, startUtc, endUtc, excludeOccurrenceId
        );

        if (conflicting.isEmpty()) {
            return ConflictDetectionResult.noConflict();
        }

        List<ConflictDetail> details = conflicting.stream()
                .map(occ -> ConflictDetail.builder()
                        .conflictingOccurrenceId(occ.getId())
                        .conflictingSeriesId(occ.getSeries().getId())
                        .conflictingTitle(occ.getSeries().getTitle())
                        .conflictingStart(occ.getStartTimeUtc())
                        .conflictingEnd(occ.getEndTimeUtc())
                        .requestedStart(startUtc)
                        .requestedEnd(endUtc)
                        .build())
                .toList();

        return ConflictDetectionResult.withConflicts(details);
    }


    @Transactional(readOnly = true)
    public ConflictDetectionResult batchCheckConflicts(
            Long roomId,
            List<OccurrenceProposal> proposals,
            Long excludeSeriesId
    ) {
        if (proposals == null || proposals.isEmpty()) {
            return ConflictDetectionResult.noConflict();
        }
        List<OccurrenceProposal> ordered = proposals.stream()
                .sorted(Comparator.comparing(OccurrenceProposal::startTimeUtc))
                .toList();
        for (int i = 1; i < ordered.size(); i++) {
            OccurrenceProposal previous = ordered.get(i - 1);
            OccurrenceProposal current = ordered.get(i);
            if (intervalsOverlap(previous.startTimeUtc(), previous.endTimeUtc(), current.startTimeUtc(), current.endTimeUtc())) {
                log.warn("Self-overlap detected between recurring proposals on {} and {}", previous.originalLocalDate(), current.originalLocalDate());
                return ConflictDetectionResult.withConflicts(List.of(ConflictDetail.builder()
                        .requestedStart(previous.startTimeUtc()).requestedEnd(previous.endTimeUtc())
                        .requestedLocalDate(previous.originalLocalDate()).conflictingStart(current.startTimeUtc())
                        .conflictingEnd(current.endTimeUtc()).conflictingTitle("Self-overlapping occurrence in same series proposal").build()));
            }
        }

        String values = String.join(",", Collections.nCopies(proposals.size(),
                "(CAST(? AS BIGINT), CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ), CAST(? AS DATE))"));
        String sql = "WITH requested(room_id,start_utc,end_utc,local_date) AS (VALUES " + values + ") " +
                "SELECT r.start_utc,r.end_utc,r.local_date,e.id,e.series_id,s.title,e.start_time_utc,e.end_time_utc " +
                "FROM requested r JOIN meeting_occurrences e ON e.room_id=r.room_id " +
                "AND e.start_time_utc < r.end_utc AND e.end_time_utc > r.start_utc " +
                "JOIN meeting_series s ON s.id=e.series_id " +
                "WHERE e.status='CONFIRMED' AND (CAST(? AS BIGINT) IS NULL OR e.series_id <> CAST(? AS BIGINT)) " +
                "ORDER BY r.start_utc,e.start_time_utc";
        List<Object> args = new ArrayList<>(proposals.size() * 4 + 2);
        for (OccurrenceProposal proposal : proposals) {
            args.add(roomId);
            args.add(java.sql.Timestamp.from(proposal.startTimeUtc()));
            args.add(java.sql.Timestamp.from(proposal.endTimeUtc()));
            args.add(java.sql.Date.valueOf(proposal.originalLocalDate()));
        }
        args.add(excludeSeriesId);
        args.add(excludeSeriesId);
        RowMapper<ConflictDetail> mapper = (rs, rowNum) -> ConflictDetail.builder()
                .requestedStart(rs.getTimestamp("start_utc").toInstant())
                .requestedEnd(rs.getTimestamp("end_utc").toInstant())
                .requestedLocalDate(rs.getDate("local_date").toLocalDate())
                .conflictingOccurrenceId(rs.getLong("id"))
                .conflictingSeriesId(rs.getLong("series_id"))
                .conflictingTitle(rs.getString("title"))
                .conflictingStart(rs.getTimestamp("start_time_utc").toInstant())
                .conflictingEnd(rs.getTimestamp("end_time_utc").toInstant())
                .build();
        return ConflictDetectionResult.withConflicts(jdbcTemplate.query(sql, mapper, args.toArray()));
    }
}
