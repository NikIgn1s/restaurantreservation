package com.restaurant.reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record BookingEvent(UUID id, UUID bookingId, BookingEventType type, String payload, Instant createdAt) {
}
