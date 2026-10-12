package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirerTest {

    @Mock
    private BookingRepository bookings;
    @Mock
    private OverbookingCoordinator overbooking;

    private Booking booking;

    @BeforeEach
    void setUp() {
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-07T12:00:00Z"), Instant.parse("2030-01-07T13:00:00Z"));
        booking = Booking.create(table, UUID.randomUUID(), slot, 2, Instant.parse("2030-01-01T09:00:00Z"));
    }

    @Test
    void expiresSavesAndLetsTheStandbyTakeOver() {
        when(bookings.save(booking)).thenReturn(booking);

        new BookingExpirer(bookings, overbooking).expire(booking);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(overbooking).onSlotFreed(booking);
    }

    @Test
    void confirmedBookingCannotExpireAndNothingIsSaved() {
        booking.confirm();

        assertThatThrownBy(() -> new BookingExpirer(bookings, overbooking).expire(booking))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(bookings, never()).save(any());
        verifyNoInteractions(overbooking);
    }
}
