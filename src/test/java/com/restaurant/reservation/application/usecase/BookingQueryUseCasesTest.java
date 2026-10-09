package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQueryUseCasesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private VenueAccessPolicy accessPolicy;

    private Actor customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-01T12:00:00Z"), Instant.parse("2030-01-01T13:00:00Z"));
        booking = Booking.create(table, customer.id(), slot, 2, NOW);
    }

    @Test
    void ownerSeesOwnBooking() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        Booking result = new GetBookingUseCase(bookings, accessPolicy)
                .execute(new BookingActionCommand(customer, booking.getId()));

        assertThat(result).isSameAs(booking);
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void venueManagerSeesForeignBooking() {
        Actor manager = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(accessPolicy.canManageResource(booking.getResourceId(), manager)).thenReturn(true);

        assertThat(new GetBookingUseCase(bookings, accessPolicy)
                .execute(new BookingActionCommand(manager, booking.getId()))).isSameAs(booking);
    }

    @Test
    void strangerCannotSeeBooking() {
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(accessPolicy.canManageResource(booking.getResourceId(), stranger)).thenReturn(false);

        assertThatThrownBy(() -> new GetBookingUseCase(bookings, accessPolicy)
                .execute(new BookingActionCommand(stranger, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetBookingUseCase(bookings, accessPolicy)
                .execute(new BookingActionCommand(customer, booking.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsBookingsOfTheActorOnly() {
        when(bookings.findByUserId(customer.id())).thenReturn(List.of(booking));

        assertThat(new ListMyBookingsUseCase(bookings).execute(customer)).containsExactly(booking);
    }
}
