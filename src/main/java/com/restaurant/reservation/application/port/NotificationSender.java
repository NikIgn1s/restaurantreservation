package com.restaurant.reservation.application.port;

public interface NotificationSender {

    void sendBookingReminder(BookingReminder reminder);
}
