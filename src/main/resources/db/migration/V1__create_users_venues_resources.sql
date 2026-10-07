CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'VENUE_OWNER', 'ADMIN'))
);

CREATE TABLE venues (
    id         UUID         PRIMARY KEY,
    owner_id   UUID         NOT NULL REFERENCES users (id),
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    address    VARCHAR(512),
    timezone   VARCHAR(64)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_venues_type CHECK (type IN ('RESTAURANT', 'COWORKING', 'SPORT'))
);

CREATE INDEX idx_venues_owner_id ON venues (owner_id);
CREATE INDEX idx_venues_active ON venues (active);

CREATE TABLE resources (
    id         UUID         PRIMARY KEY,
    venue_id   UUID         NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    capacity   INTEGER      NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_resources_capacity CHECK (capacity > 0)
);

CREATE INDEX idx_resources_venue_id ON resources (venue_id);
