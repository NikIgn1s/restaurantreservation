package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Payment;

public record DepositOutcome(Payment payment, Booking booking) {
}
