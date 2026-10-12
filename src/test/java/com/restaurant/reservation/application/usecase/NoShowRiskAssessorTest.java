package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.port.NoShowFeatures;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserBookingHistory;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoShowRiskAssessorTest {

    private static final Instant NOW = Instant.parse("2030-01-05T19:00:00Z");

    @Mock
    private BookingRepository bookings;
    @Mock
    private NoShowPredictor predictor;

    private NoShowRiskAssessor assessor;
    private UUID userId;

    @BeforeEach
    void setUp() {
        assessor = new NoShowRiskAssessor(bookings, predictor);
        userId = UUID.randomUUID();
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.3, "v1"));
    }

    private Venue venue(String zone, VenueType type) {
        return Venue.create(UUID.randomUUID(), "Place", type, "Main st 1", ZoneId.of(zone));
    }

    private TimeSlot slotAt(String start) {
        Instant from = Instant.parse(start);
        return new TimeSlot(from, from.plus(Duration.ofHours(1)));
    }

    private NoShowFeatures assess(Venue venue, TimeSlot slot, int guests) {
        when(bookings.historyOf(userId)).thenReturn(UserBookingHistory.empty());
        assessor.assess(userId, venue, slot, guests, NOW);
        ArgumentCaptor<NoShowFeatures> captor = ArgumentCaptor.forClass(NoShowFeatures.class);
        verify(predictor).predict(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTheAnswerOfThePredictor() {
        when(bookings.historyOf(userId)).thenReturn(UserBookingHistory.empty());

        NoShowPrediction prediction = assessor.assess(userId, venue("UTC", VenueType.RESTAURANT),
                slotAt("2030-01-07T19:00:00Z"), 2, NOW);

        assertThat(prediction.probability()).isEqualTo(0.3);
        assertThat(prediction.modelVersion()).isEqualTo("v1");
    }

    @Test
    void weekdayAndHourAreTakenInTheVenueTimezone() {
        NoShowFeatures features = assess(venue("Europe/Warsaw", VenueType.RESTAURANT), slotAt("2030-01-07T19:00:00Z"), 2);

        assertThat(features.dayOfWeek()).isEqualTo(1);
        assertThat(features.hour()).isEqualTo(20);
    }

    @Test
    void localDateCanDifferFromTheUtcDate() {
        NoShowFeatures features = assess(venue("Asia/Tokyo", VenueType.RESTAURANT), slotAt("2030-01-06T20:00:00Z"), 2);

        assertThat(features.dayOfWeek()).isEqualTo(1);
        assertThat(features.hour()).isEqualTo(5);
    }

    @Test
    void sundayIsDayNumberSeven() {
        NoShowFeatures features = assess(venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-06T12:00:00Z"), 2);

        assertThat(features.dayOfWeek()).isEqualTo(7);
    }

    @Test
    void leadTimeIsMeasuredInHoursFromNow() {
        NoShowFeatures features = assess(venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-07T07:30:00Z"), 2);

        assertThat(features.leadTimeHours()).isEqualTo(36.5);
    }

    @Test
    void leadTimeIsNeverNegativeAndIsCapped() {
        assertThat(assess(venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-05T18:00:00Z"), 2).leadTimeHours()).isZero();
    }

    @Test
    void veryDistantBookingsAreCappedToAYear() {
        NoShowFeatures features = assess(venue("UTC", VenueType.RESTAURANT), slotAt("2035-01-07T19:00:00Z"), 2);

        assertThat(features.leadTimeHours()).isEqualTo(8760.0);
    }

    @Test
    void guestsAndVenueTypeAreForwarded() {
        NoShowFeatures features = assess(venue("UTC", VenueType.SPORT), slotAt("2030-01-07T19:00:00Z"), 7);

        assertThat(features.partySize()).isEqualTo(7);
        assertThat(features.venueType()).isEqualTo(VenueType.SPORT);
    }

    @Test
    void hugeGroupsAreCappedForTheModel() {
        assertThat(assess(venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-07T19:00:00Z"), 400).partySize()).isEqualTo(50);
    }

    @Test
    void historyOfTheGuestIsUsed() {
        when(bookings.historyOf(userId)).thenReturn(new UserBookingHistory(12, 4));

        assessor.assess(userId, venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-07T19:00:00Z"), 2, NOW);

        ArgumentCaptor<NoShowFeatures> captor = ArgumentCaptor.forClass(NoShowFeatures.class);
        verify(predictor).predict(captor.capture());
        assertThat(captor.getValue().userBookingsCount()).isEqualTo(12);
        assertThat(captor.getValue().userNoShowRate()).isEqualTo(5.0 / 20.0);
    }

    @Test
    void newcomerGetsThePriorHistory() {
        NoShowFeatures features = assess(venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-07T19:00:00Z"), 2);

        assertThat(features.userBookingsCount()).isZero();
        assertThat(features.userNoShowRate()).isEqualTo(0.125);
    }

    @Test
    void unavailablePredictionIsPassedThrough() {
        when(bookings.historyOf(userId)).thenReturn(UserBookingHistory.empty());
        when(predictor.predict(any())).thenReturn(NoShowPrediction.unavailable());

        assertThat(assessor.assess(userId, venue("UTC", VenueType.RESTAURANT), slotAt("2030-01-07T19:00:00Z"), 2, NOW)
                .isAvailable()).isFalse();
    }
}
