package com.meetingbooking.repository;

import com.meetingbooking.entity.MeetingSeries;
import com.meetingbooking.entity.SeriesStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface MeetingSeriesRepository extends JpaRepository<MeetingSeries, Long> {

    List<MeetingSeries> findByOrganizerId(Long organizerId);

    Page<MeetingSeries> findByOrganizerId(Long organizerId, Pageable pageable);

    List<MeetingSeries> findByRoomId(Long roomId);

    /**
     * Finds active series whose materialized horizon is approaching.
     * Used by the rolling horizon background expansion job.
     */
    @Query("SELECT s FROM MeetingSeries s WHERE s.status = :status AND s.recurrenceRule IS NOT NULL " +
            "AND s.horizonEnd <= :thresholdDate AND s.startDate <= :materializationEnd " +
            "AND (s.recurrenceRule.endDate IS NULL OR s.horizonEnd < s.recurrenceRule.endDate)")
    List<MeetingSeries> findSeriesApproachingHorizon(
            @Param("status") SeriesStatus status,
            @Param("thresholdDate") LocalDate thresholdDate,
            @Param("materializationEnd") LocalDate materializationEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MeetingSeries s WHERE s.id = :id")
    Optional<MeetingSeries> findByIdForUpdate(@Param("id") Long id);
}
