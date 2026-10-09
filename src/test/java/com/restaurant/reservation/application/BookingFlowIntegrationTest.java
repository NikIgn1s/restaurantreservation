package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingFlowIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private GetAvailableSlotsUseCase availableSlots;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private CancelBookingUseCase cancelBooking;
    @Autowired
    private ConfirmBookingUseCase confirmBooking;
    @Autowired
    private BookingRepository bookings;

    private Actor owner;
    private Actor alice;
    private Actor bob;
    private Resource table;

    @BeforeEach
    void prepare() {
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        alice = new Actor(insertUser(), UserRole.CUSTOMER);
        bob = new Actor(insertUser(), UserRole.CUSTOMER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(13, 0), 60));
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private BookingResult book(Actor actor, TimeSlot slot, String key) {
        return createBooking.execute(new CreateBookingCommand(actor, table.getId(), slot, 2, key));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void createsPendingBookingAndHidesItsSlot() {
        Booking booking = book(alice, hour(11), null).booking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(availableSlots.execute(new AvailableSlotsQuery(table.getId(), MONDAY)))
                .extracting(TimeSlot::start)
                .containsExactly(hour(10).start(), hour(12).start());
    }

    @Test
    void repeatedRequestWithSameKeyReturnsTheSameBooking() {
        BookingResult first = book(alice, hour(11), "key-1");
        BookingResult second = book(alice, hour(11), "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.booking().getId()).isEqualTo(first.booking().getId());
        assertThat(count("bookings")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        book(alice, hour(11), "key-1");

        assertThatThrownBy(() -> book(alice, hour(12), "key-1")).isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void sameKeyOfDifferentUsersIsIndependent() {
        BookingResult forAlice = book(alice, hour(10), "shared-key");
        BookingResult forBob = book(bob, hour(11), "shared-key");

        assertThat(forAlice.booking().getId()).isNotEqualTo(forBob.booking().getId());
        assertThat(count("idempotency_keys")).isEqualTo(2);
    }

    @Test
    void failedRequestDoesNotStoreItsKey() {
        book(alice, hour(11), null);

        assertThatThrownBy(() -> book(bob, hour(11), "key-1")).isInstanceOf(SlotNotAvailableException.class);

        assertThat(count("idempotency_keys")).isZero();
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void secondUserCannotTakeBookedSlot() {
        book(alice, hour(11), null);

        assertThatThrownBy(() -> book(bob, hour(11), null)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void slotOutsideOpeningHoursIsRejected() {
        assertThatThrownBy(() -> book(alice, hour(14), null)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void slotNotAlignedWithTheGridIsRejected() {
        TimeSlot shifted = new TimeSlot(hour(10).start().plusSeconds(1800), hour(11).start().plusSeconds(1800));

        assertThatThrownBy(() -> book(alice, shifted, null)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void cancelledBookingFreesTheSlotForSomeoneElse() {
        Booking booking = book(alice, hour(11), null).booking();

        Booking cancelled = cancelBooking.execute(new BookingActionCommand(alice, booking.getId()));
        BookingResult rebooked = book(bob, hour(11), null);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(rebooked.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void strangerCannotCancelForeignBooking() {
        Booking booking = book(alice, hour(11), null).booking();

        assertThatThrownBy(() -> cancelBooking.execute(new BookingActionCommand(bob, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void venueOwnerCancelsCustomerBooking() {
        Booking booking = book(alice, hour(11), null).booking();

        Booking cancelled = cancelBooking.execute(new BookingActionCommand(owner, booking.getId()));

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void bookingCannotBeCancelledTwice() {
        Booking booking = book(alice, hour(11), null).booking();
        cancelBooking.execute(new BookingActionCommand(alice, booking.getId()));

        assertThatThrownBy(() -> cancelBooking.execute(new BookingActionCommand(alice, booking.getId())))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void customerConfirmsOwnBookingWhenNoDepositIsRequired() {
        Booking booking = book(alice, hour(11), null).booking();

        Booking confirmed = confirmBooking.execute(new BookingActionCommand(alice, booking.getId()));

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void customerCannotConfirmBookingThatRequiresDeposit() {
        Booking booking = bookings.findById(book(alice, hour(11), null).booking().getId()).orElseThrow();
        booking.assessNoShowRisk(0.9, 0.4);
        bookings.save(booking);

        assertThatThrownBy(() -> confirmBooking.execute(new BookingActionCommand(alice, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        Booking confirmed = confirmBooking.execute(new BookingActionCommand(owner, booking.getId()));
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmedBookingStillBlocksTheSlot() {
        Booking booking = book(alice, hour(11), null).booking();
        confirmBooking.execute(new BookingActionCommand(alice, booking.getId()));

        assertThatThrownBy(() -> book(bob, hour(11), null)).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void bookingCreatedTimestampComesFromClock() {
        Booking booking = book(alice, hour(11), null).booking();

        assertThat(booking.getCreatedAt()).isBefore(Instant.now().plusSeconds(5));
        assertThat(booking.getCreatedAt()).isAfter(Instant.now().minusSeconds(60));
    }
}
