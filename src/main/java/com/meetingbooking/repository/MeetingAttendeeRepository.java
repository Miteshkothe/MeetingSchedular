package com.meetingbooking.repository;

import com.meetingbooking.entity.MeetingAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, Long> {
    List<MeetingAttendee> findBySeriesId(Long seriesId);
    List<MeetingAttendee> findByOccurrenceId(Long occurrenceId);
    List<MeetingAttendee> findByUserId(Long userId);
}
