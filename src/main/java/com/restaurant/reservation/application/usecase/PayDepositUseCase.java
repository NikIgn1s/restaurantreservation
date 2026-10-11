package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.DepositOutcome;
import com.restaurant.reservation.application.dto.PendingDeposit;
import com.restaurant.reservation.application.port.ChargeRequest;
import com.restaurant.reservation.application.port.PaymentGateway;
import com.restaurant.reservation.application.port.PaymentResult;
import com.restaurant.reservation.domain.exception.DomainException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayDepositUseCase {

    private final DepositPaymentSteps steps;
    private final PaymentGateway gateway;

    public DepositOutcome execute(BookingActionCommand command) {
        PendingDeposit pending = steps.begin(command);
        Payment payment = pending.payment();

        PaymentResult result = charge(pending);
        if (!result.approved()) {
            log.info("Deposit payment {} was not approved: {}", payment.getId(), result.declineReason());
            return new DepositOutcome(steps.markFailed(payment.getId()), steps.loadBooking(pending.bookingId()));
        }

        Payment paid = steps.markPaid(payment.getId(), result.reference());
        try {
            Booking confirmed = steps.confirmBooking(pending.bookingId());
            return new DepositOutcome(paid, confirmed);
        } catch (DomainException e) {
            log.warn("Booking {} could not be confirmed after payment {}, refunding: {}",
                    pending.bookingId(), paid.getId(), e.getMessage());
            gateway.refund(result.reference());
            return new DepositOutcome(steps.markRefunded(paid.getId()), steps.loadBooking(pending.bookingId()));
        }
    }

    private PaymentResult charge(PendingDeposit pending) {
        Payment payment = pending.payment();
        try {
            return gateway.charge(new ChargeRequest(payment.getId(), payment.getAmount(), payment.getCurrency(),
                    pending.payerEmail()));
        } catch (RuntimeException e) {
            log.warn("Payment gateway failed for payment {}: {}", payment.getId(), e.getMessage());
            return PaymentResult.declined("Payment provider is unavailable");
        }
    }
}
