package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.application.dto.PendingDeposit;
import com.restaurant.reservation.application.port.ChargeRequest;
import com.restaurant.reservation.application.port.PaymentGateway;
import com.restaurant.reservation.application.port.PaymentResult;
import com.restaurant.reservation.domain.exception.ConcurrentBookingModificationException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Payment;
import com.restaurant.reservation.domain.model.PaymentStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class PayDepositUseCaseTest {

    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");

    @Mock
    private DepositPaymentSteps steps;
    @Mock
    private PaymentGateway gateway;

    private PayDepositUseCase useCase;
    private BookingActionCommand command;
    private Booking booking;
    private Payment payment;
    private PendingDeposit pending;

    @BeforeEach
    void setUp() {
        useCase = new PayDepositUseCase(steps, gateway);
        Actor actor = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);
        Resource table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        TimeSlot slot = new TimeSlot(Instant.parse("2030-01-07T10:00:00Z"), Instant.parse("2030-01-07T11:00:00Z"));
        booking = Booking.create(table, actor.id(), slot, 2, NOW);
        booking.assessNoShowRisk(0.9, 0.4);
        payment = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        pending = new PendingDeposit(payment, booking.getId(), "anna@example.com");
        command = new BookingActionCommand(actor, booking.getId());
    }

    @Test
    void approvedChargeConfirmsTheBooking() {
        Booking confirmed = Booking.create(Resource.create(UUID.randomUUID(), "T", 4), UUID.randomUUID(),
                new TimeSlot(Instant.parse("2030-01-08T10:00:00Z"), Instant.parse("2030-01-08T11:00:00Z")), 2, NOW);
        confirmed.confirm();
        Payment paid = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        paid.markPaid("ref-1");
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("ref-1"));
        when(steps.markPaid(payment.getId(), "ref-1")).thenReturn(paid);
        when(steps.confirmBooking(booking.getId())).thenReturn(confirmed);

        DepositOutcome outcome = useCase.execute(command);

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).refund(any());
    }

    @Test
    void chargesExactlyThePaymentThatWasStarted() {
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenReturn(PaymentResult.declined("Card declined"));
        when(steps.markFailed(payment.getId())).thenReturn(payment);
        when(steps.loadBooking(booking.getId())).thenReturn(booking);

        useCase.execute(command);

        ArgumentCaptor<ChargeRequest> captor = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(captor.capture());
        assertThat(captor.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("20.00");
        assertThat(captor.getValue().currency()).isEqualTo("PLN");
        assertThat(captor.getValue().payerEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void declinedChargeMarksPaymentFailedAndLeavesBookingPending() {
        Payment failed = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        failed.markFailed();
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenReturn(PaymentResult.declined("Card declined"));
        when(steps.markFailed(payment.getId())).thenReturn(failed);
        when(steps.loadBooking(booking.getId())).thenReturn(booking);

        DepositOutcome outcome = useCase.execute(command);

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.PENDING);
        verify(steps, never()).markPaid(any(), any());
        verify(steps, never()).confirmBooking(any());
    }

    @Test
    void gatewayOutageIsTreatedAsFailedPayment() {
        Payment failed = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        failed.markFailed();
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenThrow(new IllegalStateException("provider down"));
        when(steps.markFailed(payment.getId())).thenReturn(failed);
        when(steps.loadBooking(booking.getId())).thenReturn(booking);

        DepositOutcome outcome = useCase.execute(command);

        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(steps, never()).markPaid(any(), any());
    }

    @Test
    void chargedMoneyIsRefundedWhenBookingCannotBeConfirmed() {
        Payment paid = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        paid.markPaid("ref-1");
        Payment refunded = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        refunded.markPaid("ref-1");
        refunded.markRefunded();
        booking.cancel();
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("ref-1"));
        when(steps.markPaid(payment.getId(), "ref-1")).thenReturn(paid);
        when(steps.confirmBooking(booking.getId()))
                .thenThrow(new InvalidBookingStateException(BookingStatus.CANCELLED, BookingStatus.CONFIRMED));
        when(steps.markRefunded(paid.getId())).thenReturn(refunded);
        when(steps.loadBooking(booking.getId())).thenReturn(booking);

        DepositOutcome outcome = useCase.execute(command);

        verify(gateway).refund("ref-1");
        assertThat(outcome.payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outcome.booking().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void concurrentModificationDuringConfirmationAlsoRefunds() {
        Payment paid = Payment.start(booking.getId(), new BigDecimal("20.00"), "PLN", NOW);
        paid.markPaid("ref-1");
        when(steps.begin(command)).thenReturn(pending);
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("ref-1"));
        when(steps.markPaid(payment.getId(), "ref-1")).thenReturn(paid);
        when(steps.confirmBooking(booking.getId())).thenThrow(new ConcurrentBookingModificationException(booking.getId()));
        when(steps.markRefunded(paid.getId())).thenReturn(paid);
        when(steps.loadBooking(booking.getId())).thenReturn(booking);

        useCase.execute(command);

        verify(gateway).refund("ref-1");
        verify(steps).markRefunded(paid.getId());
    }

    @Test
    void rejectedStartNeverReachesTheGateway() {
        when(steps.begin(command)).thenThrow(new ForbiddenOperationException("not yours"));

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(gateway);
    }
}
