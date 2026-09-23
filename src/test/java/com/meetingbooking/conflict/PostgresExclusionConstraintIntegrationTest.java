package com.meetingbooking.conflict;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresExclusionConstraintIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("booking_test").withUsername("test").withPassword("test");

    @Test
    void exclusionConstraintEnforcesHalfOpenConfirmedReservations() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.execute("CREATE EXTENSION btree_gist");
        jdbc.execute("CREATE TABLE meeting_occurrences (id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL, " +
                "start_time_utc TIMESTAMPTZ NOT NULL, end_time_utc TIMESTAMPTZ NOT NULL, status VARCHAR(20) NOT NULL, " +
                "booking_range tstzrange GENERATED ALWAYS AS (tstzrange(start_time_utc,end_time_utc,'[)')) STORED, " +
                "CONSTRAINT no_overlapping_room_bookings EXCLUDE USING GIST (room_id WITH =, booking_range WITH &&) " +
                "WHERE (status='CONFIRMED'))");

        insert(jdbc, "10:00", "11:00", "CONFIRMED");
        assertDoesNotThrow(() -> insert(jdbc, "11:00", "12:00", "CONFIRMED"));
        assertThrows(RuntimeException.class, () -> insert(jdbc, "10:59:59", "11:30", "CONFIRMED"));
        assertDoesNotThrow(() -> insert(jdbc, "10:30", "11:30", "CONFLICT"));
    }

    private static void insert(JdbcTemplate jdbc, String start, String end, String status) {
        jdbc.update("INSERT INTO meeting_occurrences(room_id,start_time_utc,end_time_utc,status) " +
                        "VALUES (1, ('2026-01-01 ' || ? || ':00+00')::timestamptz, ('2026-01-01 ' || ? || ':00+00')::timestamptz, ?)",
                start, end, status);
    }
}
