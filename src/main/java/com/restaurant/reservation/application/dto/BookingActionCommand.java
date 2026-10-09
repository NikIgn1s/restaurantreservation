package com.restaurant.reservation.application.dto;

import java.util.UUID;

public record BookingActionCommand(Actor actor, UUID bookingId) {
}
