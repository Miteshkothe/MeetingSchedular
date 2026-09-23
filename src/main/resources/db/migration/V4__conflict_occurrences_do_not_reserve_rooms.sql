-- CONFLICT rows are scheduler audit records, not valid room reservations.
-- Keep only confirmed reservations in the database-level overlap constraint.
ALTER TABLE meeting_occurrences
    DROP CONSTRAINT IF EXISTS no_overlapping_room_bookings;

ALTER TABLE meeting_occurrences
    ADD CONSTRAINT no_overlapping_room_bookings EXCLUDE USING GIST (
        room_id WITH =,
        booking_range WITH &&
    ) WHERE (status = 'CONFIRMED');

DROP INDEX IF EXISTS idx_occurrences_room_time;
CREATE INDEX idx_occurrences_room_time
    ON meeting_occurrences (room_id, start_time_utc, end_time_utc)
    WHERE (status = 'CONFIRMED');
