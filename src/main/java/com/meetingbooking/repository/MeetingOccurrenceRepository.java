package com.meetingbooking.repository;

import com.meetingbooking.entity.MeetingOccurrence;
import com.meetingbooking.entity.OccurrenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingOccurrenceRepository extends JpaRepository<MeetingOccurrence, Long> {

    List<MeetingOccurrence> findBySeriesIdOrderByStartTimeUtcAsc(Long seriesId);

    Optional<MeetingOccurrence> findBySeriesIdAndOriginalLocalDate(Long seriesId, LocalDate originalLocalDate);

    Optional<MeetingOccurrence> findBySeriesIdAndStartTimeUtc(Long seriesId, Instant startTimeUtc);

    /**
     * Finds conflicting room bookings for a half-open interval [newStart, newEnd).
     * Conflict condition: existingStart < newEnd AND existingEnd > newStart.
     * Excludes cancelled occurrences and optionally the occurrence currently being edited.
     */
    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.room.id = :roomId " +
           "AND o.status = 'CONFIRMED' " +
           "AND o.startTimeUtc < :newEnd " +
           "AND o.endTimeUtc > :newStart " +
           "AND (:excludeOccurrenceId IS NULL OR o.id <> :excludeOccurrenceId)")
    List<MeetingOccurrence> findConflictingOccurrences(
            @Param("roomId") Long roomId,
            @Param("newStart") Instant newStart,
            @Param("newEnd") Instant newEnd,
            @Param("excludeOccurrenceId") Long excludeOccurrenceId
    );

    /**
     * Efficient batch retrieval for all active bookings within the overall bounding window of a recurrence series.
     */
    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.room.id = :roomId " +
           "AND o.status = 'CONFIRMED' " +
           "AND o.startTimeUtc < :windowEnd " +
           "AND o.endTimeUtc > :windowStart " +
           "ORDER BY o.startTimeUtc ASC")
    List<MeetingOccurrence> findActiveByRoomAndWindow(
            @Param("roomId") Long roomId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    /**
     * Calendar view query: date-range filtering for active bookings in a given time window.
     */
    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE (:roomId IS NULL OR o.room.id = :roomId) " +
           "AND (:organizerId IS NULL OR o.series.organizer.id = :organizerId) " +
           "AND o.startTimeUtc < :rangeEnd " +
           "AND o.endTimeUtc > :rangeStart " +
           "AND o.status = 'CONFIRMED' " +
           "ORDER BY o.startTimeUtc ASC")
    Page<MeetingOccurrence> findCalendarOccurrences(
            @Param("roomId") Long roomId,
            @Param("organizerId") Long organizerId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            Pageable pageable
    );

    /**
     * Finds occurrences in a series scheduled on or after a given date (used for THIS_AND_FUTURE edits/cancellations).
     */
    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.series.id = :seriesId " +
           "AND o.originalLocalDate >= :fromDate " +
           "ORDER BY o.startTimeUtc ASC")
    List<MeetingOccurrence> findFutureOccurrencesFromDate(
            @Param("seriesId") Long seriesId,
            @Param("fromDate") LocalDate fromDate
    );

    /**
     * Finds historical/past occurrences for data retention cleanup.
     */
    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.endTimeUtc < :cutoffTime " +
           "AND (o.status = 'CANCELLED' OR o.series.status = 'CANCELLED' OR o.series.status = 'COMPLETED')")
    List<MeetingOccurrence> findOccurrencesForRetention(@Param("cutoffTime") Instant cutoffTime);
}
