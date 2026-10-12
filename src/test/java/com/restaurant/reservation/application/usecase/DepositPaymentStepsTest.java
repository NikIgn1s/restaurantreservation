package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.PendingDeposit;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.PaymentRepository;
import com.restaurant.reservation.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class DepositPaymentStepsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private PaymentRepository payments;
    @Mock
    private UserRepository users;
    @Mock
    private OverbookingCoordinator overbooking;

    private DepositPaymentSteps steps;
    private User guest;
    private Actor actor;
    private Booking booking;

    @BeforeEach
    void setUp() {
        steps = new DepositPaymentSteps(bookings, payments, users, overbooking, Clock.fixed(NOW, ZoneOffset.UTC),
                new BigDecimal("20.00"), "PLN");
        guest = User.register("anna@example.com", "hash", "Anna", UserRole.CUSTOMER, NOW);
        actor = new Actor(guest.getId(), UserRole.CUSTOMER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-07T10:00:00Z"), Instant.parse("2030-01-07T11:00:00Z"));
        booking = Booking.create(table, guest.getId(), slot, 2, NOW);
        booking.assessNoShowRisk(0.9, 0.4);
    }

    private BookingActionCommand command() {
        return new BookingActionCommand(actor, booking.getId());
    }

    private void givenBooking() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
    }

    @Test
    void beginsPendingPaymentOfConfiguredAmount() {
        givenBooking();
        when(users.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PendingDeposit pending = steps.begin(command());

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(pending.payment());
        assertThat(pending.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pending.payment().getAmount()).isEqualByComparingTo("20.00");
        assertThat(pending.payment().getCurrency()).isEqualTo("PLN");
        assertThat(pending.payment().getBookingId()).isEqualTo(booking.getId());
        assertThat(pending.payment().getCreatedAt()).isEqualTo(NOW);
        assertThat(pending.bookingId()).isEqualTo(booking.getId());
        assertThat(pending.payerEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void unknownBookingIsNotFound() {
        when(bookings.findById(booking.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.begin(command())).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void onlyTheBookerCanPay() {
        givenBooking();
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);

        assertThatThrownBy(() -> steps.begin(new BookingActionCommand(stranger, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void venueOwnerCannotPayOnBehalfOfTheGuest() {
        givenBooking();
        Actor manager = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> steps.begin(new BookingActionCommand(manager, booking.getId())))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void confirmedBookingCannotBePaidAgain() {
        givenBooking();
        booking.confirm();

        assertThatThrownBy(() -> steps.begin(command())).isInstanceOf(InvalidBookingStateException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void cancelledBookingCannotBePaid() {
        givenBooking();
        booking.cancel();

        assertThatThrownBy(() -> steps.begin(command())).isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void bookingWithoutDepositCannotBePaid() {
        Resource table = Resource.create(UUID.randomUUID(), "Table 2", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-07T12:00:00Z"), Instant.parse("2030-01-07T13:00:00Z"));
        booking = Booking.create(table, guest.getId(), slot, 2, NOW);
        givenBooking();

        assertThatThrownBy(() -> steps.begin(command())).isInstanceOf(InvalidBookingException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void missingPayerIsNotFound() {
        givenBooking();
        when(users.findById(guest.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.begin(command())).isInstanceOf(NotFoundException.class);
        verify(payments, never()).save(any());
    }

    @Test
    void marksPaymentPaidWithReference() {
        Payment payment = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);

        Payment result = steps.markPaid(payment.getId(), "ref-1");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getExternalReference()).isEqualTo("ref-1");
    }

    @Test
    void marksPaymentFailed() {
        Payment payment = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);

        assertThat(steps.markFailed(payment.getId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void marksPaidPaymentRefunded() {
        Payment payment = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        payment.markPaid("ref-1");
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.save(payment)).thenReturn(payment);

        assertThat(steps.markRefunded(payment.getId()).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void unknownPaymentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.markPaid(id, "ref")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void confirmsBookingAfterPayment() {
        givenBooking();
        when(bookings.save(booking)).thenReturn(booking);

        assertThat(steps.confirmBooking(booking.getId()).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void paidBookingRemovesTheStandbyBehindIt() {
        givenBooking();
        when(bookings.save(booking)).thenReturn(booking);

        steps.confirmBooking(booking.getId());

        verify(overbooking).onSecured(booking);
    }

    @Test
    void confirmingCancelledBookingFails() {
        givenBooking();
        booking.cancel();

        assertThatThrownBy(() -> steps.confirmBooking(booking.getId())).isInstanceOf(InvalidBookingStateException.class);
        verify(bookings, never()).save(any());
    }
}
