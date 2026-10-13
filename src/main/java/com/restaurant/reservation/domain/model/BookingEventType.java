package com.restaurant.reservation.domain.model;

public enum BookingEventType {
    CREATED,
    STANDBY_PLACED,
    STANDBY_PROMOTED,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED,
    NO_SHOW,
    REMINDER_SENT,
    PAYMENT_STARTED,
    PAYMENT_PAID,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED
}
