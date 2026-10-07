package com.restaurant.reservation.domain.exception;

public class InvalidBookingException extends DomainException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
