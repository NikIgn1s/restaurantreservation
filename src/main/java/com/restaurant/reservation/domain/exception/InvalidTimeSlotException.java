package com.restaurant.reservation.domain.exception;

public class InvalidTimeSlotException extends DomainException {

    public InvalidTimeSlotException(String message) {
        super(message);
    }
}
