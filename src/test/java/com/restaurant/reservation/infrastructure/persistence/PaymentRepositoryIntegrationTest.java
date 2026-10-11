package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.exception.PaymentInProgressException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRepositoryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Autowired
    private PaymentRepository payments;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private ResourceRepository resources;
    @Autowired
    private VenueRepository venues;

    private Resource table;
    private UUID userId;
    private int nextHour;

    @BeforeEach
    void prepare() {
        userId = insertUser();
        Venue venue = venues.save(Venue.create(userId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        nextHour = 10;
    }

    private Booking newBooking() {
        Instant start = Instant.parse("2030-01-07T00:00:00Z").plus(Duration.ofHours(nextHour++));
        return bookings.save(Booking.create(table, userId, new TimeSlot(start, start.plus(Duration.ofHours(1))), 2, NOW));
    }

    private Payment startPayment(Booking booking) {
        return payments.save(Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW));
    }

    @Test
    void savesAndLoadsPayment() {
        Booking booking = newBooking();
        Payment saved = startPayment(booking);

        Payment loaded = payments.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getBookingId()).isEqualTo(booking.getId());
        assertThat(loaded.getAmount()).isEqualByComparingTo("20.00");
        assertThat(loaded.getCurrency()).isEqualTo("PLN");
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(loaded.getExternalReference()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void statusAndReferenceAreUpdatedInPlace() {
        Payment payment = startPayment(newBooking());
        payment.markPaid("ref-1");
        payments.save(payment);

        Payment loaded = payments.findById(payment.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(loaded.getExternalReference()).isEqualTo("ref-1");
    }

    @Test
    void unknownPaymentGivesEmpty() {
        assertThat(payments.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void secondActivePaymentForTheSameBookingIsRejected() {
        Booking booking = newBooking();
        startPayment(booking);

        assertThatThrownBy(() -> startPayment(booking)).isInstanceOf(PaymentInProgressException.class);
    }

    @Test
    void paidPaymentAlsoBlocksAnotherOne() {
        Booking booking = newBooking();
        Payment first = startPayment(booking);
        first.markPaid("ref-1");
        payments.save(first);

        assertThatThrownBy(() -> startPayment(booking)).isInstanceOf(PaymentInProgressException.class);
    }

    @Test
    void failedPaymentAllowsANewAttempt() {
        Booking booking = newBooking();
        Payment first = startPayment(booking);
        first.markFailed();
        payments.save(first);

        Payment retry = startPayment(booking);

        assertThat(retry.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.findByBookingId(booking.getId())).hasSize(2);
    }

    @Test
    void refundedPaymentAllowsANewAttempt() {
        Booking booking = newBooking();
        Payment first = startPayment(booking);
        first.markPaid("ref-1");
        first.markRefunded();
        payments.save(first);

        assertThat(startPayment(booking).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void differentBookingsDoNotBlockEachOther() {
        startPayment(newBooking());

        assertThat(startPayment(newBooking()).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void paymentsOfBookingAreReturnedNewestFirst() {
        Booking booking = newBooking();
        Payment old = payments.save(Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW));
        old.markFailed();
        payments.save(old);
        Payment recent = payments.save(Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW.plusSeconds(60)));

        assertThat(payments.findByBookingId(booking.getId())).extracting(Payment::getId)
                .containsExactly(recent.getId(), old.getId());
    }

    @Test
    void otherBookingPaymentsAreNotReturned() {
        Booking first = newBooking();
        Booking second = newBooking();
        startPayment(first);

        assertThat(payments.findByBookingId(second.getId())).isEmpty();
    }
}
