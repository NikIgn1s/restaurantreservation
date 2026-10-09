package com.restaurant.reservation.presentation.response;

import com.restaurant.reservation.domain.model.VenueType;

import java.util.UUID;

public record VenueResponse(UUID id, UUID ownerId, String name, VenueType type, String address, String timezone,
                            boolean active) {
}
