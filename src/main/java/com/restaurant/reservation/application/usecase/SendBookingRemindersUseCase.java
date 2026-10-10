package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.port.BookingReminder;
import com.restaurant.reservation.application.port.NotificationSender;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.UserRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class SendBookingRemindersUseCase {

    private final BookingRepository bookings;
    private final ResourceRepository resources;
    private final VenueRepository venues;
    private final UserRepository users;
    private final NotificationSender notifications;
    private final Clock clock;
    private final Duration leadTime;

    public SendBookingRemindersUseCase(BookingRepository bookings, ResourceRepository resources, VenueRepository venues,
                                       UserRepository users, NotificationSender notifications, Clock clock,
                                       @Value("${app.booking.reminder-lead:PT2H}") Duration leadTime) {
        this.bookings = bookings;
        this.resources = resources;
        this.venues = venues;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
        this.leadTime = leadTime;
    }

    public int execute() {
        Instant now = clock.instant();
        int sent = 0;
        for (Booking booking : bookings.findConfirmedAwaitingReminder(now, now.plus(leadTime))) {
            if (remind(booking, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean remind(Booking booking, Instant now) {
        try {
            Optional<BookingReminder> reminder = buildReminder(booking);
            if (reminder.isEmpty()) {
                return false;
            }
            notifications.sendBookingReminder(reminder.get());
            booking.markReminderSent(now);
            bookings.save(booking);
            return true;
        } catch (RuntimeException e) {
            log.warn("Reminder for booking {} was not delivered: {}", booking.getId(), e.getMessage());
            return false;
        }
    }

    private Optional<BookingReminder> buildReminder(Booking booking) {
        Optional<User> user = users.findById(booking.getUserId());
        Optional<Resource> resource = resources.findById(booking.getResourceId());
        Optional<Venue> venue = resource.flatMap(found -> venues.findById(found.getVenueId()));
        if (user.isEmpty() || resource.isEmpty() || venue.isEmpty()) {
            log.warn("Booking {} has missing user, resource or venue, reminder skipped", booking.getId());
            return Optional.empty();
        }
        return Optional.of(new BookingReminder(booking.getId(), user.get().getEmail(), user.get().getFullName(),
                venue.get().getName(), resource.get().getName(), booking.getSlot().start(), venue.get().getTimezone()));
    }
}
