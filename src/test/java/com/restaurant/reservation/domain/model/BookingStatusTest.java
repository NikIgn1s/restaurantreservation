package com.restaurant.reservation.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusTest {

    @Test
    void pendingCanBeConfirmedCancelledOrExpired() {
        assertThat(BookingStatus.PENDING.allowedTransitions())
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED);
    }

    @Test
    void confirmedCanBeCancelledCompletedOrNoShow() {
        assertThat(BookingStatus.CONFIRMED.allowedTransitions())
                .containsExactlyInAnyOrder(BookingStatus.CANCELLED, BookingStatus.COMPLETED, BookingStatus.NO_SHOW);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "EXPIRED", "COMPLETED", "NO_SHOW"})
    void finalStatusesHaveNoTransitions(BookingStatus status) {
        assertThat(status.allowedTransitions()).isEmpty();
        assertThat(status.isFinal()).isTrue();
    }

    @Test
    void pendingCannotJumpToCompleted() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.COMPLETED)).isFalse();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.NO_SHOW)).isFalse();
    }

    @Test
    void confirmedCannotGoBackToPending() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PENDING)).isFalse();
    }

    @Test
    void onlyPendingAndConfirmedOccupySlot() {
        EnumSet<BookingStatus> occupying = EnumSet.noneOf(BookingStatus.class);
        for (BookingStatus status : BookingStatus.values()) {
            if (status.occupiesSlot()) {
                occupying.add(status);
            }
        }

        assertThat(occupying).containsExactlyInAnyOrder(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    }
}
