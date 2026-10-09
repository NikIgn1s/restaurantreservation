package com.restaurant.reservation.presentation.response;

import java.time.Instant;

public record SlotResponse(Instant startAt, Instant endAt) {
}
