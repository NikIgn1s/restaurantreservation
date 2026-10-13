package com.restaurant.reservation.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record VenueBookingsQuery(Actor actor, UUID venueId, LocalDate date) {
}
