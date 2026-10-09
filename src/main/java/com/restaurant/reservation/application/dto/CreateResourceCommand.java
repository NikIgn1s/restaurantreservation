package com.restaurant.reservation.application.dto;

import java.util.UUID;

public record CreateResourceCommand(Actor actor, UUID venueId, String name, int capacity) {
}
