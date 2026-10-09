package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.TimeSlot;

import java.util.UUID;

public record CreateBookingCommand(Actor actor, UUID resourceId, TimeSlot slot, int guests, String idempotencyKey) {

    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }
}
