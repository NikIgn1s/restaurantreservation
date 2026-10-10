package com.restaurant.reservation.application.event;

import java.util.UUID;

public record VenueSlotsChanged(UUID venueId) {
}
