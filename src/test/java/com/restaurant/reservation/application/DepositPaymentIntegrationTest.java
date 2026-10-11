package com.restaurant.reservation.application;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.application.port.PaymentGateway;
import com.restaurant.reservation.application.port.PaymentResult;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.GetBookingPaymentsUseCase;
import com.restaurant.reservation.application.usecase.PayDepositUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.PaymentInProgressException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DepositPaymentIntegrationTest extends PostgresIntegrationTest {

    private static final Instant CREATED = Instant.parse("2029-12-01T00:00:00Z");

    @Autowired
    private CreateVenueUseCase createVenue;
    @Autowired
    private CreateResourceUseCase createResource;
    @Autowired
    private PayDepositUseCase payDeposit;
    @Autowired
    private GetBookingPaymentsUseCase getPayments;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private PaymentRepository payments;
    @SpyBean
    private PaymentGateway gateway;

    private ExecutorService executor;
    private Actor owner;
    private Actor guest;
    private Resource table;
    private int nextHour;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(6);
        owner = new Actor(insertUser(), UserRole.VENUE_OWNER);
        guest = new Actor(insertUser(), UserRole.CUSTOMER);
        Venue venue = createVenue.execute(
                new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = createResource.execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));
        nextHour = 10;
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private Booking bookingFor(Actor actor, boolean depositRequired) {
        Instant start = Instant.parse("2030-01-07T00:00:00Z").plus(Duration.ofHours(nextHour++));
        Booking booking = Booking.create(table, actor.id(), new TimeSlot(start, start.plus(Duration.ofHours(1))), 2, CREATED);
        if (depositRequired) {
            booking.assessNoShowRisk(0.9, 0.4);
        }
        return bookings.save(booking);
    }

    private BookingActionCommand pay(Actor actor, Booking booking) {
        return new BookingActionCommand(actor, booking.getId());
    }

    private Booking reload(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow();
    }

    private Actor guestWithEmail(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, 'hash', 'Guest', 'CUSTOMER')",
                id, email);
        return new Actor(id, UserRole.CUSTOMER);
    }

    @Test
    void successfulPaymentConfirmsTheBooking() {
        Booking booking = bookingFor(guest, true);

        DepositOutcome outcome = payDeposit.execute(pay(guest, booking));

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(outcome.payment().getExternalReference()).isEqualTo("fake-" + outcome.payment().getId());
        assertThat(outcome.payment().getAmount()).isEqualByComparingTo("20.00");
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(payments.findByBookingId(booking.getId())).extracting(Payment::getStatus).containsExactly(PaymentStatus.PAID);
    }

    @Test
    void declinedPaymentLeavesBookingPendingAndIsRecorded() {
        Actor declined = guestWithEmail("decline.me@test.com");
        Booking booking = bookingFor(declined, true);

        DepositOutcome outcome = payDeposit.execute(pay(declined, booking));

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(payments.findByBookingId(booking.getId())).extracting(Payment::getStatus).containsExactly(PaymentStatus.FAILED);
    }

    @Test
    void afterADeclineThePayerCanTryAgain() {
        Booking booking = bookingFor(guest, true);
        doReturn(PaymentResult.declined("Card declined")).doCallRealMethod().when(gateway).charge(any());

        DepositOutcome first = payDeposit.execute(pay(guest, booking));
        DepositOutcome second = payDeposit.execute(pay(guest, booking));

        assertThat(first.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(second.payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(second.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(payments.findByBookingId(booking.getId())).extracting(Payment::getStatus)
                .containsExactly(PaymentStatus.PAID, PaymentStatus.FAILED);
    }

    @Test
    void providerOutageIsRecordedAsFailedPayment() {
        Booking booking = bookingFor(guest, true);
        doThrow(new IllegalStateException("provider down")).when(gateway).charge(any());

        DepositOutcome outcome = payDeposit.execute(pay(guest, booking));

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void moneyIsRefundedWhenTheBookingWasCancelledWhileCharging() {
        Booking booking = bookingFor(guest, true);
        doAnswer(invocation -> {
            Booking current = reload(booking);
            current.cancel();
            bookings.save(current);
            return invocation.callRealMethod();
        }).when(gateway).charge(any());

        DepositOutcome outcome = payDeposit.execute(pay(guest, booking));

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(gateway).refund("fake-" + outcome.payment().getId());
        assertThat(payments.findById(outcome.payment().getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void successfulPaymentIsNotRefunded() {
        payDeposit.execute(pay(guest, bookingFor(guest, true)));

        verify(gateway, never()).refund(anyString());
    }

    @Test
    void strangerCannotPayForSomeoneElsesBooking() {
        Booking booking = bookingFor(guest, true);
        Actor stranger = new Actor(insertUser(), UserRole.CUSTOMER);

        assertThatThrownBy(() -> payDeposit.execute(pay(stranger, booking))).isInstanceOf(ForbiddenOperationException.class);

        assertThat(payments.findByBookingId(booking.getId())).isEmpty();
        verify(gateway, never()).charge(any());
    }

    @Test
    void bookingWithoutDepositCannotBePaid() {
        Booking booking = bookingFor(guest, false);

        assertThatThrownBy(() -> payDeposit.execute(pay(guest, booking))).isInstanceOf(InvalidBookingException.class);

        assertThat(payments.findByBookingId(booking.getId())).isEmpty();
    }

    @Test
    void paidBookingCannotBePaidTwice() {
        Booking booking = bookingFor(guest, true);
        payDeposit.execute(pay(guest, booking));

        assertThatThrownBy(() -> payDeposit.execute(pay(guest, booking))).isInstanceOf(InvalidBookingStateException.class);

        verify(gateway, times(1)).charge(any());
    }

    @Test
    void cancelledBookingCannotBePaid() {
        Booking booking = bookingFor(guest, true);
        booking.cancel();
        bookings.save(booking);

        assertThatThrownBy(() -> payDeposit.execute(pay(guest, booking))).isInstanceOf(InvalidBookingStateException.class);

        verify(gateway, never()).charge(any());
    }

    @Test
    void simultaneousPaymentsChargeOnlyOnce() throws Exception {
        Booking booking = bookingFor(guest, true);
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Object> task = () -> {
                ready.countDown();
                start.await();
                try {
                    return payDeposit.execute(pay(guest, booking));
                } catch (Throwable error) {
                    return error;
                }
            };
            futures.add(executor.submit(task));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        assertThat(results).filteredOn(DepositOutcome.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(Throwable.class::isInstance)
                .allSatisfy(error -> assertThat(error).isInstanceOfAny(PaymentInProgressException.class, InvalidBookingStateException.class));
        verify(gateway, times(1)).charge(any());
        assertThat(payments.findByBookingId(booking.getId())).extracting(Payment::getStatus).containsExactly(PaymentStatus.PAID);
        assertThat(reload(booking).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void bookerSeesPaymentsOfTheBooking() {
        Booking booking = bookingFor(guest, true);
        payDeposit.execute(pay(guest, booking));

        assertThat(getPayments.execute(pay(guest, booking))).hasSize(1);
    }

    @Test
    void venueOwnerSeesPaymentsOfCustomerBooking() {
        Booking booking = bookingFor(guest, true);
        payDeposit.execute(pay(guest, booking));

        assertThat(getPayments.execute(pay(owner, booking))).hasSize(1);
    }

    @Test
    void strangerCannotSeePayments() {
        Booking booking = bookingFor(guest, true);
        Actor stranger = new Actor(insertUser(), UserRole.CUSTOMER);

        assertThatThrownBy(() -> getPayments.execute(pay(stranger, booking))).isInstanceOf(ForbiddenOperationException.class);
    }
}
