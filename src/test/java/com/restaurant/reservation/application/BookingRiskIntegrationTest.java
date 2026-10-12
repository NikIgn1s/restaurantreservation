package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.SetWeeklyScheduleCommand;
import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.application.usecase.CompleteBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.MarkNoShowUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.application.usecase.SetWeeklyScheduleUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserBookingHistory;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingRiskIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    private static final Instant LONG_AGO = Instant.parse("2019-01-01T00:00:00Z");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private SetWeeklyScheduleUseCase setSchedule;
    @Autowired
    private CreateBookingUseCase createBooking;
    @Autowired
    private ConfirmBookingUseCase confirmBooking;
    @Autowired
    private PayDepositUseCase payDeposit;
    @Autowired
    private CompleteBookingUseCase completeBooking;
    @Autowired
    private MarkNoShowUseCase markNoShow;
    @Autowired
    private BookingRepository bookings;
    @MockBean
    private NoShowPredictor predictor;

    private Actor owner;
    private Actor guest;
    private Resource table;

    @BeforeEach
    void prepare() {
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        guest = new Actor(insertUser(), UserRole.CUSTOMER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        setSchedule.execute(new SetWeeklyScheduleCommand(owner, table.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(16, 0), 60));
    }

    private static TimeSlot hour(int start) {
        return new TimeSlot(MONDAY.atTime(start, 0).toInstant(ZoneOffset.UTC), MONDAY.atTime(start + 1, 0).toInstant(ZoneOffset.UTC));
    }

    private Booking book(Actor actor, int startHour) {
        return createBooking.execute(new CreateBookingCommand(actor, table.getId(), hour(startHour), 2, null)).booking();
    }

    private Booking visitInThePast(Actor actor, BookingStatus finalStatus, int daysAgo) {
        Instant start = Instant.now().minus(Duration.ofDays(daysAgo));
        Booking booking = Booking.create(table, actor.id(), new TimeSlot(start, start.plus(Duration.ofHours(1))), 2, LONG_AGO);
        booking.confirm();
        if (finalStatus == BookingStatus.COMPLETED) {
            booking.complete();
        } else if (finalStatus == BookingStatus.NO_SHOW) {
            booking.markNoShow();
        }
        return bookings.save(booking);
    }

    private NoShowFeatures capturedFeatures() {
        ArgumentCaptor<NoShowFeatures> captor = ArgumentCaptor.forClass(NoShowFeatures.class);
        verify(predictor).predict(captor.capture());
        return captor.getValue();
    }

    @Test
    void highRiskBookingRequiresDepositAndStoresTheProbability() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.72, "v1"));

        Booking booking = book(guest, 11);
        Booking stored = bookings.findById(booking.getId()).orElseThrow();

        assertThat(stored.isDepositRequired()).isTrue();
        assertThat(stored.getNoShowProbability()).isEqualTo(0.72);
    }

    @Test
    void lowRiskBookingHasNoDeposit() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.08, "v1"));

        Booking stored = bookings.findById(book(guest, 11).getId()).orElseThrow();

        assertThat(stored.isDepositRequired()).isFalse();
        assertThat(stored.getNoShowProbability()).isEqualTo(0.08);
    }

    @Test
    void probabilityExactlyAtTheThresholdDoesNotRequireDeposit() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.4, "v1"));

        assertThat(book(guest, 11).isDepositRequired()).isFalse();
    }

    @Test
    void unavailableModelDoesNotBlockBookingAndLeavesRiskUnknown() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.unavailable());

        Booking stored = bookings.findById(book(guest, 11).getId()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(stored.getNoShowProbability()).isNull();
        assertThat(stored.isDepositRequired()).isFalse();
    }

    @Test
    void predictorReceivesTheFeaturesOfTheBooking() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.1, "v1"));

        book(guest, 15);

        NoShowFeatures features = capturedFeatures();
        assertThat(features.dayOfWeek()).isEqualTo(1);
        assertThat(features.hour()).isEqualTo(15);
        assertThat(features.partySize()).isEqualTo(2);
        assertThat(features.venueType()).isEqualTo(VenueType.RESTAURANT);
        assertThat(features.leadTimeHours()).isPositive();
        assertThat(features.userBookingsCount()).isZero();
        assertThat(features.userNoShowRate()).isEqualTo(0.125);
    }

    @Test
    void historyOfFinishedVisitsFeedsThePrediction() {
        visitInThePast(guest, BookingStatus.NO_SHOW, 30);
        visitInThePast(guest, BookingStatus.NO_SHOW, 20);
        visitInThePast(guest, BookingStatus.COMPLETED, 10);
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.1, "v1"));

        book(guest, 11);

        NoShowFeatures features = capturedFeatures();
        assertThat(features.userBookingsCount()).isEqualTo(3);
        assertThat(features.userNoShowRate()).isEqualTo(3.0 / 11.0);
    }

    @Test
    void onlyCompletedAndNoShowVisitsCountAsHistory() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.05, "v1"));
        Booking pending = book(guest, 10);
        Booking cancelled = book(guest, 11);
        cancelled.cancel();
        bookings.save(cancelled);
        Booking confirmedButNotFinished = book(guest, 12);
        confirmBooking.execute(new BookingActionCommand(guest, confirmedButNotFinished.getId()));

        UserBookingHistory history = bookings.historyOf(guest.id());

        assertThat(pending.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(history).isEqualTo(UserBookingHistory.empty());
    }

    @Test
    void historyBelongsToTheGuestOnly() {
        Actor other = new Actor(insertUser(), UserRole.CUSTOMER);
        visitInThePast(other, BookingStatus.NO_SHOW, 10);
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.1, "v1"));

        book(guest, 11);

        assertThat(capturedFeatures().userBookingsCount()).isZero();
    }

    @Test
    void riskyGuestPaysTheDepositAndTheBookingGetsConfirmed() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.9, "v1"));
        Booking booking = book(guest, 11);

        assertThatThrownBy(() -> confirmBooking.execute(new BookingActionCommand(guest, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        var outcome = payDeposit.execute(new BookingActionCommand(guest, booking.getId()));

        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(outcome.payment().getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void safeGuestCanConfirmWithoutPaying() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.05, "v1"));
        Booking booking = book(guest, 11);

        Booking confirmed = confirmBooking.execute(new BookingActionCommand(guest, booking.getId()));

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void venueOwnerRecordsNoShowAndItRaisesTheNextPrediction() {
        Booking visit = visitInThePast(guest, BookingStatus.CONFIRMED, 1);

        Booking result = markNoShow.execute(new BookingActionCommand(owner, visit.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(bookings.historyOf(guest.id())).isEqualTo(new UserBookingHistory(1, 1));
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.1, "v1"));
        book(guest, 11);
        assertThat(capturedFeatures().userNoShowRate()).isEqualTo(2.0 / 9.0);
    }

    @Test
    void venueOwnerRecordsAnAttendedVisit() {
        Booking visit = visitInThePast(guest, BookingStatus.CONFIRMED, 1);

        Booking result = completeBooking.execute(new BookingActionCommand(owner, visit.getId()));

        assertThat(result.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(bookings.historyOf(guest.id())).isEqualTo(new UserBookingHistory(1, 0));
    }

    @Test
    void guestCannotRecordTheirOwnAttendance() {
        Booking visit = visitInThePast(guest, BookingStatus.CONFIRMED, 1);

        assertThatThrownBy(() -> completeBooking.execute(new BookingActionCommand(guest, visit.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> markNoShow.execute(new BookingActionCommand(guest, visit.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(bookings.findById(visit.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void outcomeCannotBeRecordedBeforeTheVisitStarts() {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.05, "v1"));
        Booking future = book(guest, 11);
        confirmBooking.execute(new BookingActionCommand(guest, future.getId()));

        assertThatThrownBy(() -> markNoShow.execute(new BookingActionCommand(owner, future.getId())))
                .isInstanceOf(InvalidBookingException.class);
        assertThat(bookings.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
