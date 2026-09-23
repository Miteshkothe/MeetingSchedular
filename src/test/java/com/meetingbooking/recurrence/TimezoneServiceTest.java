package com.meetingbooking.recurrence;

import com.meetingbooking.exception.NonexistentLocalTimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class TimezoneServiceTest {

    private TimezoneService timezoneService;

    @BeforeEach
    void setUp() {
        timezoneService = new TimezoneService();
    }

    @Test
    @DisplayName("Should convert local time in Asia/Kolkata to UTC Instant correctly")
    void testToUtcInstantKolkata() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        LocalTime time = LocalTime.of(10, 0, 0);
        ZoneId zoneId = ZoneId.of("Asia/Kolkata"); // UTC+05:30

        Instant instant = timezoneService.toUtcInstant(date, time, zoneId);

        // 10:00 AM IST = 04:30 AM UTC
        assertEquals(Instant.parse("2026-09-23T04:30:00Z"), instant);
    }

    @Test
    @DisplayName("Attendee in America/Los_Angeles should see correct local time from UTC Instant")
    void testAttendeeLocalConversion() {
        // 10:00 AM IST on Sep 23 2026 is 04:30 AM UTC
        Instant instant = Instant.parse("2026-09-23T04:30:00Z");
        ZoneId laZone = ZoneId.of("America/Los_Angeles"); // In Sep, PDT is UTC-7

        LocalDateTime laDateTime = timezoneService.toAttendeeLocal(instant, laZone);

        // 04:30 UTC on Sep 23 minus 7 hours is Sep 22 at 21:30 (9:30 PM)
        assertEquals(LocalDate.of(2026, 9, 22), laDateTime.toLocalDate());
        assertEquals(LocalTime.of(21, 30, 0), laDateTime.toLocalTime());
    }

    @Test
    @DisplayName("DST Spring-Forward: Should reject nonexistent local time during clock forward transition")
    void testDstSpringForwardGapRejection() {
        // In America/New_York on 2024-03-10, at 2:00 AM clocks jump to 3:00 AM.
        // 02:30:00 local time does not exist.
        LocalDate date = LocalDate.of(2024, 3, 10);
        LocalTime time = LocalTime.of(2, 30, 0);
        ZoneId nyZone = ZoneId.of("America/New_York");

        NonexistentLocalTimeException ex = assertThrows(NonexistentLocalTimeException.class, () ->
                timezoneService.toUtcInstant(date, time, nyZone)
        );

        assertEquals("America/New_York", ex.getTimezone());
        assertEquals(date, ex.getLocalDate());
        assertEquals(time, ex.getLocalTime());
    }

    @Test
    @DisplayName("DST Fall-Back: Should deterministically resolve ambiguous local time to earlier offset")
    void testDstFallBackOverlapResolution() {
        // In America/New_York on 2024-11-03, at 2:00 AM clocks fall back to 1:00 AM.
        // 01:30:00 occurs twice: first as EDT (UTC-4), then as EST (UTC-5).
        // Documented policy: earlier offset (EDT, UTC-4), which corresponds to 05:30:00Z.
        LocalDate date = LocalDate.of(2024, 11, 3);
        LocalTime time = LocalTime.of(1, 30, 0);
        ZoneId nyZone = ZoneId.of("America/New_York");

        Instant instant = timezoneService.toUtcInstant(date, time, nyZone);

        assertEquals(Instant.parse("2024-11-03T05:30:00Z"), instant);
    }
}
