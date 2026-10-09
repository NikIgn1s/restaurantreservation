package com.restaurant.reservation.domain.service;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;

import java.time.Instant;
import java.util.List;

public final class SlotAvailabilityCalculator {

    private SlotAvailabilityCalculator() {
    }

    public static List<TimeSlot> freeSlots(List<TimeSlot> candidates, List<Booking> activeBookings, Instant now) {
        return candidates.stream()
                .filter(slot -> !slot.startsBefore(now))
                .filter(slot -> activeBookings.stream()
                        .filter(booking -> booking.getStatus().occupiesSlot())
                        .noneMatch(booking -> booking.getSlot().overlaps(slot)))
                .toList();
    }
}
