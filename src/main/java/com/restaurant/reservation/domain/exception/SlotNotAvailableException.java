package com.restaurant.reservation.domain.exception;

public class SlotNotAvailableException extends DomainException {

    public SlotNotAvailableException(String message) {
        super(message);
    }
}
