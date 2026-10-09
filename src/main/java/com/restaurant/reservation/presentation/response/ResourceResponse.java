package com.restaurant.reservation.presentation.response;

import java.util.UUID;

public record ResourceResponse(UUID id, UUID venueId, String name, int capacity, boolean active) {
}
