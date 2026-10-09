package com.restaurant.reservation.application.port;

import java.util.UUID;

public record IdempotencyRecord(String requestHash, UUID bookingId) {
}
