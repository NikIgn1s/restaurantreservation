package com.restaurant.reservation.application.event;

import java.util.UUID;

public record ResourceSlotsChanged(UUID resourceId) {
}
