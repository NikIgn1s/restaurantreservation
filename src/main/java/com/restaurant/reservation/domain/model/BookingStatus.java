package com.restaurant.reservation.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED,
    NO_SHOW;

    public Set<BookingStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(CANCELLED, COMPLETED, NO_SHOW);
            case CANCELLED, EXPIRED, COMPLETED, NO_SHOW -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canTransitionTo(BookingStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean occupiesSlot() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
