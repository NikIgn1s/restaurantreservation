ALTER TABLE bookings ADD COLUMN overbooked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bookings DROP CONSTRAINT ex_bookings_no_overlap;

ALTER TABLE bookings ADD CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (
    resource_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED') AND NOT overbooked);

ALTER TABLE bookings ADD CONSTRAINT ex_bookings_single_overbooked EXCLUDE USING gist (
    resource_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED') AND overbooked);
