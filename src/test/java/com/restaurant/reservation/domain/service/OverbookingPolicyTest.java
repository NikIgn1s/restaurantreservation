package com.restaurant.reservation.domain.service;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OverbookingPolicyTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final double MIN_PROBABILITY = 0.5;

    private Resource table;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        slot = new TimeSlot(Instant.parse("2030-01-07T12:00:00Z"), Instant.parse("2030-01-07T13:00:00Z"));
    }

    private Booking booking(double probability) {
        Booking booking = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        booking.assessNoShowRisk(probability, 0.4);
        return booking;
    }

    @Test
    void allowsWhenTheOnlyBookingIsLikelyToBeANoShow() {
        assertThat(OverbookingPolicy.allows(List.of(booking(0.8)), MIN_PROBABILITY)).isTrue();
    }

    @Test
    void allowsAtExactlyTheMinimumProbability() {
        assertThat(OverbookingPolicy.allows(List.of(booking(0.5)), MIN_PROBABILITY)).isTrue();
    }

    @Test
    void refusesWhenTheOnlyBookingIsReliable() {
        assertThat(OverbookingPolicy.allows(List.of(booking(0.45)), MIN_PROBABILITY)).isFalse();
    }

    @Test
    void refusesWhenThereIsNoBookingAtAll() {
        assertThat(OverbookingPolicy.allows(List.of(), MIN_PROBABILITY)).isFalse();
    }

    @Test
    void refusesWhenTheSlotAlreadyHasAStandby() {
        Booking standby = booking(0.9);
        standby.markOverbooked();

        assertThat(OverbookingPolicy.allows(List.of(booking(0.9), standby), MIN_PROBABILITY)).isFalse();
    }

    @Test
    void refusesWhenTheOnlyBookingIsConfirmed() {
        Booking confirmed = booking(0.9);
        confirmed.confirm();

        assertThat(OverbookingPolicy.allows(List.of(confirmed), MIN_PROBABILITY)).isFalse();
    }

    @Test
    void refusesWhenTheOnlyBookingHasNoPrediction() {
        Booking unknown = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);

        assertThat(OverbookingPolicy.allows(List.of(unknown), MIN_PROBABILITY)).isFalse();
    }
}
