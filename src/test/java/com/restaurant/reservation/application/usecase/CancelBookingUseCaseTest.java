package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelBookingUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private VenueAccessPolicy accessPolicy;
    @Mock
    private OverbookingCoordinator overbooking;

    private CancelBookingUseCase useCase;
    private Actor customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        useCase = new CancelBookingUseCase(bookings, accessPolicy, overbooking);
        customer = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-01T12:00:00Z"), Instant.parse("2030-01-01T13:00:00Z"));
        booking = Booking.create(table, customer.id(), slot, 2, NOW);
    }

    private void givenBookingExists() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
    }

    @Test
    void ownerCancelsOwnBooking() {
        givenBookingExists();
        when(bookings.save(booking)).thenReturn(booking);

        Booking result = useCase.execute(new BookingActionCommand(customer, booking.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void freedSlotIsOfferedToTheStandby() {
        givenBookingExists();
        when(bookings.save(booking)).thenReturn(booking);

        useCase.execute(new BookingActionCommand(customer, booking.getId()));

        verify(overbooking).onSlotFreed(booking);
    }

    @Test
    void rejectedCancellationLeavesTheStandbyAlone() {
        givenBookingExists();
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        when(accessPolicy.canManageResource(booking.getResourceId(), stranger)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(stranger, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(overbooking);
    }

    @Test
    void venueManagerCancelsForeignBooking() {
        givenBookingExists();
        Actor manager = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        when(accessPolicy.canManageResource(booking.getResourceId(), manager)).thenReturn(true);
        when(bookings.save(booking)).thenReturn(booking);

        Booking result = useCase.execute(new BookingActionCommand(manager, booking.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void strangerCannotCancel() {
        givenBookingExists();
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        when(accessPolicy.canManageResource(booking.getResourceId(), stranger)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(stranger, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings, never()).save(any());
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotCancelTwice() {
        givenBookingExists();
        booking.cancel();

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(InvalidBookingStateException.class);
        verify(bookings, never()).save(any());
    }

    @Test
    void cannotCancelCompletedBooking() {
        givenBookingExists();
        booking.confirm();
        booking.complete();

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(InvalidBookingStateException.class);
    }
}
