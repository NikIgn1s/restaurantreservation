package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserBookingHistory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID id);

    Optional<Booking> lockById(UUID id);

    List<Booking> findByUserId(UUID userId);

    List<Booking> findActiveByResourceAndSlot(UUID resourceId, TimeSlot slot);

    List<Booking> findPendingCreatedBefore(Instant threshold);

    List<Booking> findConfirmedStartingBetween(Instant from, Instant to);

    List<Booking> findConfirmedAwaitingReminder(Instant from, Instant to);

    UserBookingHistory historyOf(UUID userId);
}
