ALTER TABLE bookings ADD COLUMN reminder_sent_at TIMESTAMPTZ;

CREATE INDEX idx_bookings_awaiting_reminder ON bookings (start_at)
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;
