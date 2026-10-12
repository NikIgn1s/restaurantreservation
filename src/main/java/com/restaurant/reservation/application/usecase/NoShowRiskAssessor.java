package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserBookingHistory;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NoShowRiskAssessor {

    private static final double MAX_LEAD_TIME_HOURS = 8760.0;
    private static final int MAX_PARTY_SIZE = 50;

    private final BookingRepository bookings;
    private final NoShowPredictor predictor;

    public NoShowPrediction assess(UUID userId, Venue venue, TimeSlot slot, int guests, Instant now) {
        UserBookingHistory history = bookings.historyOf(userId);
        ZonedDateTime localStart = slot.start().atZone(venue.getTimezone());
        double leadTimeHours = Duration.between(now, slot.start()).toMinutes() / 60.0;
        NoShowFeatures features = new NoShowFeatures(
                localStart.getDayOfWeek().getValue(),
                localStart.getHour(),
                Math.min(Math.max(leadTimeHours, 0.0), MAX_LEAD_TIME_HOURS),
                Math.min(guests, MAX_PARTY_SIZE),
                history.finishedBookings(),
                history.noShowRate(),
                venue.getType());
        return predictor.predict(features);
    }
}
