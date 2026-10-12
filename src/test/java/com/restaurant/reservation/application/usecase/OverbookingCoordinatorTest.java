package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverbookingCoordinatorTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingRepository bookings;

    private OverbookingCoordinator coordinator;
    private Resource table;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        coordinator = new OverbookingCoordinator(bookings);
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        slot = new TimeSlot(Instant.parse("2030-01-07T12:00:00Z"), Instant.parse("2030-01-07T13:00:00Z"));
    }

    private Booking primary() {
        Booking booking = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        booking.assessNoShowRisk(0.8, 0.4);
        return booking;
    }

    private Booking standby() {
        Booking booking = Booking.create(table, UUID.randomUUID(), slot, 2, NOW);
        booking.assessNoShowRisk(0.1, 0.4);
        booking.markOverbooked();
        return booking;
    }

    @Test
    void standbyTakesOverWhenThePrimaryIsCancelled() {
        Booking primary = primary();
        Booking standby = standby();
        primary.cancel();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(standby));

        coordinator.onSlotFreed(primary);

        assertThat(standby.isOverbooked()).isFalse();
        assertThat(standby.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings).save(standby);
    }

    @Test
    void standbyTakesOverWhenThePrimaryExpires() {
        Booking primary = primary();
        Booking standby = standby();
        primary.expire();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(standby));

        coordinator.onSlotFreed(primary);

        assertThat(standby.isOverbooked()).isFalse();
    }

    @Test
    void nothingHappensWhenThereIsNoStandby() {
        Booking primary = primary();
        primary.cancel();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of());

        coordinator.onSlotFreed(primary);

        verify(bookings, never()).save(any());
    }

    @Test
    void cancellingAStandbyDoesNotPromoteAnyone() {
        Booking cancelledStandby = standby();
        cancelledStandby.cancel();

        coordinator.onSlotFreed(cancelledStandby);

        verifyNoInteractions(bookings);
    }

    @Test
    void stillActiveBookingFreesNothing() {
        coordinator.onSlotFreed(primary());

        verifyNoInteractions(bookings);
    }

    @Test
    void onlyOneStandbyIsPromoted() {
        Booking primary = primary();
        Booking first = standby();
        Booking second = standby();
        primary.cancel();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(first, second));

        coordinator.onSlotFreed(primary);

        assertThat(first.isOverbooked()).isFalse();
        assertThat(second.isOverbooked()).isTrue();
    }

    @Test
    void standbyIsCancelledWhenThePrimaryIsSecured() {
        Booking primary = primary();
        Booking standby = standby();
        primary.confirm();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(primary, standby));

        coordinator.onSecured(primary);

        assertThat(standby.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(primary.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings).save(standby);
        verify(bookings, never()).save(primary);
    }

    @Test
    void securedPrimaryWithoutStandbyChangesNothing() {
        Booking primary = primary();
        primary.confirm();
        when(bookings.findActiveByResourceAndSlot(table.getId(), slot)).thenReturn(List.of(primary));

        coordinator.onSecured(primary);

        verify(bookings, never()).save(any());
    }

    @Test
    void confirmingAStandbyDoesNotCancelAnything() {
        Booking standby = standby();
        standby.confirm();

        coordinator.onSecured(standby);

        verifyNoInteractions(bookings);
    }
}
