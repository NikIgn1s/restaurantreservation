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
class ConfirmBookingUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private VenueAccessPolicy accessPolicy;
    @Mock
    private OverbookingCoordinator overbooking;

    private ConfirmBookingUseCase useCase;
    private Actor customer;
    private Actor manager;
    private Booking booking;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmBookingUseCase(bookings, accessPolicy, overbooking);
        customer = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        manager = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-01T12:00:00Z"), Instant.parse("2030-01-01T13:00:00Z"));
        booking = Booking.create(table, customer.id(), slot, 2, NOW);
    }

    private void givenBookingExists() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
    }

    private void givenSaveReturnsBooking() {
        when(bookings.save(booking)).thenReturn(booking);
    }

    private void givenManagerRights(Actor actor, boolean allowed) {
        when(accessPolicy.canManageResource(booking.getResourceId(), actor)).thenReturn(allowed);
    }

    @Test
    void managerConfirmsBooking() {
        givenBookingExists();
        givenManagerRights(manager, true);
        givenSaveReturnsBooking();

        Booking result = useCase.execute(new BookingActionCommand(manager, booking.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmationRemovesTheStandbyBehindTheBooking() {
        givenBookingExists();
        givenManagerRights(manager, true);
        givenSaveReturnsBooking();

        useCase.execute(new BookingActionCommand(manager, booking.getId()));

        verify(overbooking).onSecured(booking);
    }

    @Test
    void forbiddenConfirmationLeavesTheStandbyAlone() {
        givenBookingExists();
        booking.assessNoShowRisk(0.9, 0.4);
        givenManagerRights(customer, false);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(overbooking);
    }

    @Test
    void managerConfirmsBookingThatRequiresDeposit() {
        givenBookingExists();
        booking.assessNoShowRisk(0.9, 0.4);
        givenManagerRights(manager, true);
        givenSaveReturnsBooking();

        assertThat(useCase.execute(new BookingActionCommand(manager, booking.getId())).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void ownerConfirmsOwnBookingWithoutDeposit() {
        givenBookingExists();
        givenManagerRights(customer, false);
        givenSaveReturnsBooking();

        Booking result = useCase.execute(new BookingActionCommand(customer, booking.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void ownerCannotConfirmBookingThatRequiresDeposit() {
        givenBookingExists();
        booking.assessNoShowRisk(0.9, 0.4);
        givenManagerRights(customer, false);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(bookings, never()).save(any());
    }

    @Test
    void strangerCannotConfirm() {
        givenBookingExists();
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        givenManagerRights(stranger, false);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(stranger, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(manager, booking.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotConfirmCancelledBooking() {
        givenBookingExists();
        booking.cancel();
        givenManagerRights(manager, true);

        assertThatThrownBy(() -> useCase.execute(new BookingActionCommand(manager, booking.getId())))
                .isInstanceOf(InvalidBookingStateException.class);
        verify(bookings, never()).save(any());
    }
}
