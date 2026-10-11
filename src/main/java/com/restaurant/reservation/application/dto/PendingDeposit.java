package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.Payment;

import java.util.UUID;

public record PendingDeposit(Payment payment, UUID bookingId, String payerEmail) {
}
