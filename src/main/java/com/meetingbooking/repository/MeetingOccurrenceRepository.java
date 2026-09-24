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

    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.series.id = :seriesId " +
           "AND o.originalLocalDate >= :fromDate " +
           "ORDER BY o.startTimeUtc ASC")
    List<MeetingOccurrence> findFutureOccurrencesFromDate(
            @Param("seriesId") Long seriesId,
            @Param("fromDate") LocalDate fromDate
    );


    @Query("SELECT o FROM MeetingOccurrence o " +
           "WHERE o.endTimeUtc < :cutoffTime " +
           "AND (o.status = 'CANCELLED' OR o.series.status = 'CANCELLED' OR o.series.status = 'COMPLETED')")
    List<MeetingOccurrence> findOccurrencesForRetention(@Param("cutoffTime") Instant cutoffTime);
}
