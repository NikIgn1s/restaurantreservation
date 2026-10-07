CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE schedules (
    id           UUID        PRIMARY KEY,
    resource_id  UUID        NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    day_of_week  SMALLINT    NOT NULL,
    open_time    TIME        NOT NULL,
    close_time   TIME        NOT NULL,
    slot_minutes INTEGER     NOT NULL,
    CONSTRAINT uq_schedules_resource_day UNIQUE (resource_id, day_of_week),
    CONSTRAINT ck_schedules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_schedules_hours CHECK (open_time < close_time),
    CONSTRAINT ck_schedules_slot CHECK (slot_minutes > 0)
);

CREATE TABLE schedule_exceptions (
    id          UUID    PRIMARY KEY,
    resource_id UUID    NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    date        DATE    NOT NULL,
    closed      BOOLEAN NOT NULL,
    open_time   TIME,
    close_time  TIME,
    CONSTRAINT uq_schedule_exceptions_resource_date UNIQUE (resource_id, date),
    CONSTRAINT ck_schedule_exceptions_hours CHECK (
        (closed AND open_time IS NULL AND close_time IS NULL)
        OR (NOT closed AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time)
    )
);

CREATE TABLE bookings (
    id                  UUID             PRIMARY KEY,
    resource_id         UUID             NOT NULL REFERENCES resources (id),
    user_id             UUID             NOT NULL REFERENCES users (id),
    start_at            TIMESTAMPTZ      NOT NULL,
    end_at              TIMESTAMPTZ      NOT NULL,
    guests              INTEGER          NOT NULL,
    status              VARCHAR(32)      NOT NULL,
    deposit_required    BOOLEAN          NOT NULL DEFAULT FALSE,
    no_show_probability DOUBLE PRECISION,
    version             BIGINT           NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_bookings_period CHECK (start_at < end_at),
    CONSTRAINT ck_bookings_guests CHECK (guests > 0),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT ck_bookings_probability CHECK (no_show_probability IS NULL OR no_show_probability BETWEEN 0 AND 1),
    CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_resource_start ON bookings (resource_id, start_at);
CREATE INDEX idx_bookings_status_created ON bookings (status, created_at);
