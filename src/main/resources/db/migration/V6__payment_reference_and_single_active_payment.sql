ALTER TABLE payments ADD COLUMN external_reference VARCHAR(128);

CREATE UNIQUE INDEX uq_payments_active_booking ON payments (booking_id)
    WHERE status IN ('PENDING', 'PAID');
