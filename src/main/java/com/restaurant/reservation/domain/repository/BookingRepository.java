package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(UUID id);

    List<Booking> findByUserId(UUID userId);

    List<Booking> findActiveByResourceAndSlot(UUID resourceId, TimeSlot slot);

    List<Booking> findPendingCreatedBefore(Instant threshold);

    List<Booking> findConfirmedStartingBetween(Instant from, Instant to);

    List<Booking> findConfirmedAwaitingReminder(Instant from, Instant to);
}
