package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final double DEPOSIT_THRESHOLD = 0.4;

    private final UUID userId = UUID.randomUUID();
    private final Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
    private final TimeSlot futureSlot = new TimeSlot(NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofHours(5)));

    private Booking newBooking() {
        return Booking.create(table, userId, futureSlot, 2, NOW);
    }

    @Test
    void newBookingIsPendingWithoutDeposit() {
        Booking booking = newBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.isDepositRequired()).isFalse();
        assertThat(booking.getNoShowProbability()).isNull();
        assertThat(booking.getResourceId()).isEqualTo(table.getId());
        assertThat(booking.getUserId()).isEqualTo(userId);
        assertThat(booking.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsBookingInThePast() {
        TimeSlot pastSlot = new TimeSlot(NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> Booking.create(table, userId, pastSlot, 2, NOW))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void rejectsBookingStartingBeforeNow() {
        TimeSlot startedSlot = new TimeSlot(NOW.minusSeconds(1), NOW.plus(Duration.ofHours(1)));

        assertThatThrownBy(() -> Booking.create(table, userId, startedSlot, 2, NOW))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void allowsBookingStartingExactlyNow() {
        TimeSlot slotNow = new TimeSlot(NOW, NOW.plus(Duration.ofHours(1)));

        assertThat(Booking.create(table, userId, slotNow, 2, NOW).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void rejectsGuestsAboveCapacity() {
        assertThatThrownBy(() -> Booking.create(table, userId, futureSlot, 5, NOW))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void acceptsGuestsEqualToCapacity() {
        assertThat(Booking.create(table, userId, futureSlot, 4, NOW).getGuests()).isEqualTo(4);
    }

    @Test
    void rejectsZeroGuests() {
        assertThatThrownBy(() -> Booking.create(table, userId, futureSlot, 0, NOW))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void rejectsInactiveResource() {
        table.deactivate();

        assertThatThrownBy(() -> newBooking()).isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void requiresDepositWhenProbabilityAboveThreshold() {
        Booking booking = newBooking();

        booking.assessNoShowRisk(0.41, DEPOSIT_THRESHOLD);

        assertThat(booking.isDepositRequired()).isTrue();
        assertThat(booking.getNoShowProbability()).isEqualTo(0.41);
    }

    @Test
    void doesNotRequireDepositWhenProbabilityEqualsThreshold() {
        Booking booking = newBooking();

        booking.assessNoShowRisk(0.4, DEPOSIT_THRESHOLD);

        assertThat(booking.isDepositRequired()).isFalse();
    }

    @Test
    void doesNotRequireDepositWhenProbabilityBelowThreshold() {
        Booking booking = newBooking();

        booking.assessNoShowRisk(0.1, DEPOSIT_THRESHOLD);

        assertThat(booking.isDepositRequired()).isFalse();
        assertThat(booking.getNoShowProbability()).isEqualTo(0.1);
    }

    @Test
    void pendingBookingCanBeConfirmed() {
        Booking booking = newBooking();

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void pendingBookingCanBeCancelled() {
        Booking booking = newBooking();

        booking.cancel();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void pendingBookingCanExpire() {
        Booking booking = newBooking();

        booking.expire();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void confirmedBookingCanBeCompleted() {
        Booking booking = newBooking();
        booking.confirm();

        booking.complete();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void confirmedBookingCanBeMarkedNoShow() {
        Booking booking = newBooking();
        booking.confirm();

        booking.markNoShow();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void pendingBookingCannotBeCompleted() {
        Booking booking = newBooking();

        assertThatThrownBy(booking::complete).isInstanceOf(InvalidBookingStateException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void pendingBookingCannotBeMarkedNoShow() {
        Booking booking = newBooking();

        assertThatThrownBy(booking::markNoShow).isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void cancelledBookingCannotBeConfirmed() {
        Booking booking = newBooking();
        booking.cancel();

        assertThatThrownBy(booking::confirm).isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void confirmedBookingCannotExpire() {
        Booking booking = newBooking();
        booking.confirm();

        assertThatThrownBy(booking::expire).isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking booking = newBooking();
        booking.confirm();
        booking.complete();

        assertThatThrownBy(booking::cancel).isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void ownershipIsCheckedByUserId() {
        Booking booking = newBooking();

        assertThat(booking.isOwnedBy(userId)).isTrue();
        assertThat(booking.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
