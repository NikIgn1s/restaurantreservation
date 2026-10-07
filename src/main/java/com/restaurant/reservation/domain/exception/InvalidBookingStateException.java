package com.restaurant.reservation.domain.exception;

import com.restaurant.reservation.domain.model.BookingStatus;

public class InvalidBookingStateException extends DomainException {

    public InvalidBookingStateException(BookingStatus from, BookingStatus to) {
        super("Booking cannot move from " + from + " to " + to);
    }
}
