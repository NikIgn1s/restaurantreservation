package com.restaurant.reservation.domain.service;

import com.restaurant.reservation.domain.model.Booking;

import java.util.List;

public final class OverbookingPolicy {

    private OverbookingPolicy() {
    }

    public static boolean allows(List<Booking> activeBookingsOfSlot, double minProbability) {
        return activeBookingsOfSlot.size() == 1 && activeBookingsOfSlot.get(0).isOverbookable(minProbability);
    }
}
