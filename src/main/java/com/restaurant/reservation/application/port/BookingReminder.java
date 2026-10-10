package com.restaurant.reservation.application.port;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record BookingReminder(UUID bookingId, String recipientEmail, String recipientName, String venueName,
                              String resourceName, Instant startAt, ZoneId timezone) {
}
