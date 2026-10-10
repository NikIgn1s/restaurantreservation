package com.restaurant.reservation.infrastructure.scheduler;

import com.restaurant.reservation.application.port.BookingReminder;
import com.restaurant.reservation.application.port.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void sendBookingReminder(BookingReminder reminder) {
        ZonedDateTime local = reminder.startAt().atZone(reminder.timezone());
        log.info("Reminder to {} <{}>: booking {} at {} ({}) starts {}", reminder.recipientName(),
                reminder.recipientEmail(), reminder.bookingId(), reminder.venueName(), reminder.resourceName(), local);
    }
}
