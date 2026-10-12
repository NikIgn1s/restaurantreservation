package com.restaurant.reservation.domain.model;

public record UserBookingHistory(int finishedBookings, int noShows) {

    private static final double PRIOR_NO_SHOWS = 1.0;
    private static final double PRIOR_BOOKINGS = 8.0;

    public UserBookingHistory {
        if (finishedBookings < 0 || noShows < 0 || noShows > finishedBookings) {
            throw new IllegalArgumentException("History counts are inconsistent");
        }
    }

    public static UserBookingHistory empty() {
        return new UserBookingHistory(0, 0);
    }

    public double noShowRate() {
        return (noShows + PRIOR_NO_SHOWS) / (finishedBookings + PRIOR_BOOKINGS);
    }
}
