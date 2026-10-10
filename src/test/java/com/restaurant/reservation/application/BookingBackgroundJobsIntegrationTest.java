package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.port.BookingReminder;
import com.restaurant.reservation.application.port.NotificationSender;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ExpireUnpaidBookingsUseCase;
import com.restaurant.reservation.application.usecase.SendBookingRemindersUseCase;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BookingBackgroundJobsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private ExpireUnpaidBookingsUseCase expireUnpaidBookings;
    @Autowired
    private SendBookingRemindersUseCase sendReminders;
    @MockBean
    private NotificationSender notifications;

    private Resource table;
    private UUID guestId;

    @BeforeEach
    void prepare() {
        Actor owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        guestId = insertUser();
    }

    private Instant hoursFromNow(long hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }

    private Booking book(Instant start, Instant createdAt) {
        TimeSlot slot = new TimeSlot(start, start.plus(Duration.ofHours(1)));
        return bookings.save(Booking.create(table, guestId, slot, 2, createdAt));
    }

    private Booking confirmed(Booking booking) {
        booking.confirm();
        return bookings.save(booking);
    }

    private BookingStatus statusOf(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow().getStatus();
    }

    @Test
    void staleUnpaidBookingExpires() {
        Booking stale = book(hoursFromNow(48), Instant.now().minus(Duration.ofMinutes(45)));

        int expired = expireUnpaidBookings.execute();

        assertThat(expired).isEqualTo(1);
        assertThat(statusOf(stale)).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void freshPendingBookingIsLeftAlone() {
        Booking fresh = book(hoursFromNow(48), Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(expireUnpaidBookings.execute()).isZero();

        assertThat(statusOf(fresh)).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void confirmedBookingNeverExpires() {
        Booking old = confirmed(book(hoursFromNow(48), Instant.now().minus(Duration.ofHours(5))));

        assertThat(expireUnpaidBookings.execute()).isZero();

        assertThat(statusOf(old)).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void expiredBookingFreesItsSlot() {
        Instant start = hoursFromNow(48);
        book(start, Instant.now().minus(Duration.ofHours(1)));
        expireUnpaidBookings.execute();

        Booking next = book(start, Instant.now());

        assertThat(next.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void expirationHandlesManyBookingsInOneRun() {
        for (int i = 0; i < 5; i++) {
            book(hoursFromNow(48 + i), Instant.now().minus(Duration.ofHours(2)));
        }
        Booking fresh = book(hoursFromNow(80), Instant.now());

        assertThat(expireUnpaidBookings.execute()).isEqualTo(5);

        assertThat(statusOf(fresh)).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void reminderIsSentOnceForBookingStartingSoon() {
        Booking soon = confirmed(book(hoursFromNow(1), Instant.now().minus(Duration.ofHours(3))));

        assertThat(sendReminders.execute()).isEqualTo(1);
        assertThat(sendReminders.execute()).isZero();

        ArgumentCaptor<BookingReminder> captor = ArgumentCaptor.forClass(BookingReminder.class);
        verify(notifications).sendBookingReminder(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(soon.getId());
        assertThat(captor.getValue().venueName()).isEqualTo("Bistro");
        assertThat(captor.getValue().resourceName()).isEqualTo("Table 1");
        assertThat(captor.getValue().recipientEmail()).isEqualTo(guestId + "@test.com");
        assertThat(bookings.findById(soon.getId()).orElseThrow().getReminderSentAt()).isNotNull();
    }

    @Test
    void bookingFarInTheFutureGetsNoReminderYet() {
        confirmed(book(hoursFromNow(24), Instant.now().minus(Duration.ofHours(3))));

        assertThat(sendReminders.execute()).isZero();

        verifyNoInteractions(notifications);
    }

    @Test
    void unconfirmedAndCancelledBookingsGetNoReminder() {
        book(hoursFromNow(1), Instant.now());
        Booking cancelled = confirmed(book(hoursFromNow(1).plus(Duration.ofHours(3)), Instant.now()));
        cancelled.cancel();
        bookings.save(cancelled);

        assertThat(sendReminders.execute()).isZero();

        verifyNoInteractions(notifications);
    }

    @Test
    void failedDeliveryIsRetriedOnTheNextRun() {
        Booking soon = confirmed(book(hoursFromNow(1), Instant.now().minus(Duration.ofHours(3))));
        doThrow(new IllegalStateException("smtp down")).doNothing().when(notifications).sendBookingReminder(any());

        assertThat(sendReminders.execute()).isZero();
        assertThat(bookings.findById(soon.getId()).orElseThrow().getReminderSentAt()).isNull();
        assertThat(sendReminders.execute()).isEqualTo(1);
        assertThat(bookings.findById(soon.getId()).orElseThrow().getReminderSentAt()).isNotNull();
    }

    @Test
    void alreadyStartedBookingGetsNoReminder() {
        TimeSlot started = new TimeSlot(Instant.now().minus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofMinutes(30)));
        Booking booking = bookings.save(Booking.create(table, guestId, started, 2, Instant.now().minus(Duration.ofHours(2))));
        confirmed(booking);

        assertThat(sendReminders.execute()).isZero();

        verify(notifications, never()).sendBookingReminder(any());
    }
}
