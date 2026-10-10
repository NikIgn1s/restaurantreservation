package com.restaurant.reservation.domain.exception;

import java.util.UUID;

public class ConcurrentBookingModificationException extends DomainException {

    public ConcurrentBookingModificationException(UUID bookingId) {
        super("Booking " + bookingId + " was modified by someone else, reload it and try again");
    }
}
