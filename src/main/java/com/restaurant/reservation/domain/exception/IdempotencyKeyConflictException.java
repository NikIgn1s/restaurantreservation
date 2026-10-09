package com.restaurant.reservation.domain.exception;

public class IdempotencyKeyConflictException extends DomainException {

    public IdempotencyKeyConflictException(String key) {
        super("Idempotency key " + key + " was already used with a different request");
    }
}
