package com.restaurant.reservation.presentation.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.reservation.domain.model.BookingEventType;

import java.time.Instant;
import java.util.UUID;

public record BookingEventResponse(UUID id, BookingEventType type, JsonNode payload, Instant createdAt) {
}
