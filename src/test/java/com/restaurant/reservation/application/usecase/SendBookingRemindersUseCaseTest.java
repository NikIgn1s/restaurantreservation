package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.port.BookingReminder;
import com.restaurant.reservation.application.port.NotificationSender;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.UserRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendBookingRemindersUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");
    private static final Duration LEAD = Duration.ofHours(2);

    @Mock
    private BookingRepository bookings;
    @Mock
    private ResourceRepository resources;
    @Mock
    private VenueRepository venues;
    @Mock
    private UserRepository users;
    @Mock
    private NotificationSender notifications;

    private SendBookingRemindersUseCase useCase;
    private User guest;
    private Venue venue;
    private Resource table;

    @BeforeEach
    void setUp() {
        useCase = new SendBookingRemindersUseCase(bookings, resources, venues, users, notifications,
                Clock.fixed(NOW, ZoneOffset.UTC), LEAD);
        guest = User.register("anna@example.com", "hash", "Anna Kowalska", UserRole.CUSTOMER, NOW);
        venue = Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw"));
        table = Resource.create(venue.getId(), "Table 1", 4);
    }

    private Booking confirmedBookingStartingInOneHour() {
        TimeSlot slot = new TimeSlot(NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(2)));
        Booking booking = Booking.create(table, guest.getId(), slot, 2, NOW.minus(Duration.ofDays(1)));
        booking.confirm();
        return booking;
    }

    private void givenReferenceData() {
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
    }

    @Test
    void searchesWindowFromNowToLeadTime() {
        when(bookings.findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD))).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();

        verify(bookings).findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD));
        verifyNoInteractions(notifications);
    }

    @Test
    void sendsReminderWithBookingDetailsAndMarksIt() {
        Booking booking = confirmedBookingStartingInOneHour();
        when(bookings.findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD))).thenReturn(List.of(booking));
        givenReferenceData();
        when(bookings.save(booking)).thenReturn(booking);

        assertThat(useCase.execute()).isEqualTo(1);

        ArgumentCaptor<BookingReminder> captor = ArgumentCaptor.forClass(BookingReminder.class);
        verify(notifications).sendBookingReminder(captor.capture());
        BookingReminder reminder = captor.getValue();
        assertThat(reminder.bookingId()).isEqualTo(booking.getId());
        assertThat(reminder.recipientEmail()).isEqualTo("anna@example.com");
        assertThat(reminder.recipientName()).isEqualTo("Anna Kowalska");
        assertThat(reminder.venueName()).isEqualTo("Bistro");
        assertThat(reminder.resourceName()).isEqualTo("Table 1");
        assertThat(reminder.startAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(reminder.timezone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
        assertThat(booking.getReminderSentAt()).isEqualTo(NOW);
        verify(bookings).save(booking);
    }

    @Test
    void doesNotMarkBookingWhenSendingFails() {
        Booking booking = confirmedBookingStartingInOneHour();
        when(bookings.findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD))).thenReturn(List.of(booking));
        givenReferenceData();
        doThrow(new IllegalStateException("smtp down")).when(notifications).sendBookingReminder(any());

        assertThat(useCase.execute()).isZero();

        assertThat(booking.getReminderSentAt()).isNull();
        assertThat(booking.needsReminder()).isTrue();
        verify(bookings, never()).save(any());
    }

    @Test
    void continuesWithNextBookingAfterFailure() {
        Booking failing = confirmedBookingStartingInOneHour();
        Booking healthy = confirmedBookingStartingInOneHour();
        when(bookings.findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD))).thenReturn(List.of(failing, healthy));
        givenReferenceData();
        doThrow(new IllegalStateException("smtp down"))
                .doNothing()
                .when(notifications).sendBookingReminder(any());
        when(bookings.save(healthy)).thenReturn(healthy);

        assertThat(useCase.execute()).isEqualTo(1);

        assertThat(failing.needsReminder()).isTrue();
        assertThat(healthy.needsReminder()).isFalse();
    }

    @Test
    void skipsBookingWhenUserIsGone() {
        Booking booking = confirmedBookingStartingInOneHour();
        when(bookings.findConfirmedAwaitingReminder(NOW, NOW.plus(LEAD))).thenReturn(List.of(booking));
        when(users.findById(guest.getId())).thenReturn(Optional.empty());
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        assertThat(useCase.execute()).isZero();

        verifyNoInteractions(notifications);
        verify(bookings, never()).save(any());
    }
}
