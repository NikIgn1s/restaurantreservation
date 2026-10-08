CREATE TABLE payments (
    id         UUID           PRIMARY KEY,
    booking_id UUID           NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    amount     NUMERIC(12, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    status     VARCHAR(32)    NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payments_booking_id ON payments (booking_id);

CREATE TABLE booking_events (
    id         UUID         PRIMARY KEY,
    booking_id UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    type       VARCHAR(64)  NOT NULL,
    payload    JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_events_booking_id ON booking_events (booking_id, created_at);

CREATE TABLE idempotency_keys (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INTEGER      NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
