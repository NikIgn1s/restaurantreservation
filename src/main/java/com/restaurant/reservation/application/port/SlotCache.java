package com.restaurant.reservation.application.port;

import com.restaurant.reservation.domain.model.TimeSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotCache {

    Optional<List<TimeSlot>> get(UUID resourceId, LocalDate date);

    void put(UUID resourceId, LocalDate date, List<TimeSlot> slots);

    void evict(UUID resourceId, LocalDate date);

    void evictResource(UUID resourceId);
}
