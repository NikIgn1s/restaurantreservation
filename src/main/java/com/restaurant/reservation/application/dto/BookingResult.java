package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.Booking;

public record BookingResult(Booking booking, boolean replayed) {
}
