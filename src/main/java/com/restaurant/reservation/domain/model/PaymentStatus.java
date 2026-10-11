package com.restaurant.reservation.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED;

    public Set<PaymentStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(PAID, FAILED);
            case PAID -> EnumSet.of(REFUNDED);
            case FAILED, REFUNDED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isActive() {
        return this == PENDING || this == PAID;
    }
}
