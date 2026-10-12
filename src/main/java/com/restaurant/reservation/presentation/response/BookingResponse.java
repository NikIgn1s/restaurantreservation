package com.restaurant.reservation.presentation.response;

import com.restaurant.reservation.domain.model.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(UUID id, UUID resourceId, UUID userId, Instant startAt, Instant endAt, int guests,
                              BookingStatus status, boolean depositRequired, Double noShowProbability,
                              boolean overbooked, Instant createdAt, long version) {
}
