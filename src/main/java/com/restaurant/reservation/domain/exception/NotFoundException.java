package com.restaurant.reservation.domain.exception;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
