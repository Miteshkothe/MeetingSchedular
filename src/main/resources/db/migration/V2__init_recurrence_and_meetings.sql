-- V2: Recurrence and Meetings with PostgreSQL GiST exclusion constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE recurrence_rules (
    id BIGSERIAL PRIMARY KEY,
    frequency VARCHAR(20) NOT NULL,
    interval_value INT NOT NULL DEFAULT 1 CHECK (interval_value >= 1),
    weekdays VARCHAR(100),
    day_of_month INT CHECK (day_of_month BETWEEN 1 AND 31),
    week_number INT CHECK (week_number BETWEEN -1 AND 5),
    weekday VARCHAR(20),
    end_date DATE,
    occurrence_count INT CHECK (occurrence_count IS NULL OR occurrence_count > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE meeting_series (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    organizer_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    timezone VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    recurrence_rule_id BIGINT REFERENCES recurrence_rules(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    horizon_end DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_series_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_series_times CHECK (end_time > start_time)
);

CREATE TABLE meeting_occurrences (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL REFERENCES meeting_series(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    start_time_utc TIMESTAMPTZ NOT NULL,
    end_time_utc TIMESTAMPTZ NOT NULL,
    original_local_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    is_exception BOOLEAN NOT NULL DEFAULT FALSE,
    booking_range tstzrange GENERATED ALWAYS AS (tstzrange(start_time_utc, end_time_utc, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_occurrence_time_order CHECK (end_time_utc > start_time_utc),
    CONSTRAINT uq_series_occurrence_start UNIQUE (series_id, start_time_utc),
    CONSTRAINT no_overlapping_room_bookings EXCLUDE USING GIST (
        room_id WITH =,
        booking_range WITH &&
    ) WHERE (status = 'CONFIRMED')
);

CREATE TABLE meeting_attendees (
    id BIGSERIAL PRIMARY KEY,
    series_id BIGINT REFERENCES meeting_series(id) ON DELETE CASCADE,
    occurrence_id BIGINT REFERENCES meeting_occurrences(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    attendance_status VARCHAR(50) NOT NULL DEFAULT 'INVITED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_attendee_target CHECK (
        (series_id IS NOT NULL AND occurrence_id IS NULL) OR
        (series_id IS NULL AND occurrence_id IS NOT NULL) OR
        (series_id IS NOT NULL AND occurrence_id IS NOT NULL)
    )
);

-- Purpose-driven performance indexes
CREATE INDEX idx_occurrences_room_time ON meeting_occurrences (room_id, start_time_utc, end_time_utc) WHERE (status = 'CONFIRMED');
CREATE INDEX idx_occurrences_series_id ON meeting_occurrences (series_id);
CREATE INDEX idx_occurrences_calendar ON meeting_occurrences (start_time_utc, end_time_utc);
CREATE INDEX idx_series_organizer ON meeting_series (organizer_id);
CREATE INDEX idx_series_room ON meeting_series (room_id);
CREATE INDEX idx_series_status_horizon ON meeting_series (status, horizon_end);
CREATE INDEX idx_attendees_user ON meeting_attendees (user_id);
CREATE INDEX idx_attendees_series ON meeting_attendees (series_id);
CREATE INDEX idx_attendees_occurrence ON meeting_attendees (occurrence_id);
