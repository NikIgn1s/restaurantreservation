package com.restaurant.reservation.application.port;

import com.restaurant.reservation.domain.model.VenueType;

public record NoShowFeatures(int dayOfWeek, int hour, double leadTimeHours, int partySize, int userBookingsCount,
                             double userNoShowRate, VenueType venueType) {
}
