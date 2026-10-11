package com.restaurant.reservation.domain.exception;

import com.restaurant.reservation.domain.model.PaymentStatus;

public class InvalidPaymentStateException extends DomainException {

    public InvalidPaymentStateException(PaymentStatus from, PaymentStatus to) {
        super("Payment cannot move from " + from + " to " + to);
    }
}
