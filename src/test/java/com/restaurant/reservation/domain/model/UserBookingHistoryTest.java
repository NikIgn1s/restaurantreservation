package com.restaurant.reservation.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserBookingHistoryTest {

    @Test
    void newcomerGetsThePriorRate() {
        assertThat(UserBookingHistory.empty().noShowRate()).isEqualTo(0.125);
    }

    @Test
    void rateIsSmoothedTowardsThePrior() {
        assertThat(new UserBookingHistory(2, 2).noShowRate()).isEqualTo(0.3);
        assertThat(new UserBookingHistory(10, 0).noShowRate()).isEqualTo(1.0 / 18.0);
    }

    @Test
    void longHistoryDominatesThePrior() {
        assertThat(new UserBookingHistory(1000, 500).noShowRate()).isEqualTo(501.0 / 1008.0);
    }

    @Test
    void rateStaysWithinTheUnitInterval() {
        assertThat(new UserBookingHistory(5, 5).noShowRate()).isBetween(0.0, 1.0);
        assertThat(new UserBookingHistory(5, 0).noShowRate()).isBetween(0.0, 1.0);
    }

    @Test
    void rejectsInconsistentCounts() {
        assertThatThrownBy(() -> new UserBookingHistory(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserBookingHistory(3, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserBookingHistory(3, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
