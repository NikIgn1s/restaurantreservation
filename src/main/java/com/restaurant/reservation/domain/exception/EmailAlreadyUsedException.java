package com.restaurant.reservation.domain.exception;

public class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException(String email) {
        super("Email " + email + " is already registered");
    }
}
