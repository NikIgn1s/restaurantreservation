package com.restaurant.reservation.presentation.response;

import com.restaurant.reservation.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, UUID bookingId, BigDecimal amount, String currency, PaymentStatus status,
                              String reference, Instant createdAt) {
}
