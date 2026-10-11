package com.restaurant.reservation.domain.exception;

import java.util.UUID;

public class PaymentInProgressException extends DomainException {

    public PaymentInProgressException(UUID bookingId) {
        super("Booking " + bookingId + " already has a payment in progress or completed");
    }
}
