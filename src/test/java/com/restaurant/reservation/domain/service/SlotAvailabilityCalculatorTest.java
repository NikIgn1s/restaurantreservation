package com.restaurant.reservation.domain.service;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotAvailabilityCalculatorTest {

    private static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant OPEN = Instant.parse("2030-01-01T10:00:00Z");

    private final Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);

    private static TimeSlot hour(int index) {
        return new TimeSlot(OPEN.plus(Duration.ofHours(index)), OPEN.plus(Duration.ofHours(index + 1)));
    }

    private Booking booking(TimeSlot slot) {
        return Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
    }

    @Test
    void returnsAllCandidatesWhenNothingIsBooked() {
        List<TimeSlot> candidates = List.of(hour(0), hour(1), hour(2));

        assertThat(SlotAvailabilityCalculator.freeSlots(candidates, List.of(), NOW)).containsExactlyElementsOf(candidates);
    }

    @Test
    void removesSlotsOverlappedByActiveBookings() {
        List<TimeSlot> candidates = List.of(hour(0), hour(1), hour(2));

        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(candidates, List.of(booking(hour(1))), NOW);

        assertThat(free).containsExactly(hour(0), hour(2));
    }

    @Test
    void longBookingBlocksEverySlotItCovers() {
        TimeSlot twoHours = new TimeSlot(hour(0).start(), hour(1).end());

        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(
                List.of(hour(0), hour(1), hour(2)), List.of(booking(twoHours)), NOW);

        assertThat(free).containsExactly(hour(2));
    }

    @Test
    void adjacentBookingDoesNotBlockSlot() {
        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(List.of(hour(1)), List.of(booking(hour(0))), NOW);

        assertThat(free).containsExactly(hour(1));
    }

    @Test
    void ignoresCancelledBookings() {
        Booking cancelled = booking(hour(1));
        cancelled.cancel();

        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(List.of(hour(1)), List.of(cancelled), NOW);

        assertThat(free).containsExactly(hour(1));
    }

    @Test
    void dropsSlotsThatAlreadyStarted() {
        Instant afterFirstSlotStart = OPEN.plus(Duration.ofMinutes(30));

        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(
                List.of(hour(0), hour(1)), List.of(), afterFirstSlotStart);

        assertThat(free).containsExactly(hour(1));
    }

    @Test
    void keepsSlotStartingExactlyNow() {
        List<TimeSlot> free = SlotAvailabilityCalculator.freeSlots(List.of(hour(0)), List.of(), OPEN);

        assertThat(free).containsExactly(hour(0));
    }
}
