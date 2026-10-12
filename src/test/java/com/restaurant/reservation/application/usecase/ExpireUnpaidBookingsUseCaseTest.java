package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpireUnpaidBookingsUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    @Mock
    private BookingRepository bookings;
    @Mock
    private OverbookingCoordinator overbooking;

    private ExpireUnpaidBookingsUseCase useCase;
    private Resource table;

    @BeforeEach
    void setUp() {
        useCase = new ExpireUnpaidBookingsUseCase(bookings, new BookingExpirer(bookings, overbooking), Clock.fixed(NOW, ZoneOffset.UTC), TTL);
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
    }

    private Booking pendingBooking(int startHour) {
        TimeSlot slot = new TimeSlot(Instant.parse("2030-02-01T00:00:00Z").plus(Duration.ofHours(startHour)),
                Instant.parse("2030-02-01T01:00:00Z").plus(Duration.ofHours(startHour)));
        return Booking.create(table, UUID.randomUUID(), slot, 2, NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void looksForBookingsOlderThanTtl() {
        when(bookings.findPendingCreatedBefore(NOW.minus(TTL))).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();

        verify(bookings).findPendingCreatedBefore(NOW.minus(TTL));
        verify(bookings, never()).save(any());
    }

    @Test
    void expiresAndSavesEveryStaleBooking() {
        Booking first = pendingBooking(1);
        Booking second = pendingBooking(2);
        when(bookings.findPendingCreatedBefore(NOW.minus(TTL))).thenReturn(List.of(first, second));
        when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int expired = useCase.execute();

        assertThat(expired).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        verify(bookings).save(first);
        verify(bookings).save(second);
    }

    @Test
    void skipsBookingThatIsNoLongerPending() {
        Booking confirmed = pendingBooking(1);
        confirmed.confirm();
        Booking stale = pendingBooking(2);
        when(bookings.findPendingCreatedBefore(NOW.minus(TTL))).thenReturn(List.of(confirmed, stale));
        when(bookings.save(stale)).thenReturn(stale);

        assertThat(useCase.execute()).isEqualTo(1);

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookings, never()).save(confirmed);
    }

    @Test
    void continuesAfterConcurrentModification() {
        Booking raced = pendingBooking(1);
        Booking stale = pendingBooking(2);
        when(bookings.findPendingCreatedBefore(NOW.minus(TTL))).thenReturn(List.of(raced, stale));
        when(bookings.save(raced)).thenThrow(new ConcurrentBookingModificationException(raced.getId()));
        when(bookings.save(stale)).thenReturn(stale);

        assertThat(useCase.execute()).isEqualTo(1);

        verify(bookings).save(stale);
    }
}
