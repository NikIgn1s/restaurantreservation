package com.restaurant.reservation.infrastructure.cache;

import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.model.TimeSlot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false")
public class NoOpSlotCache implements SlotCache {

    @Override
    public Optional<List<TimeSlot>> get(UUID resourceId, LocalDate date) {
        return Optional.empty();
    }

    @Override
    public void put(UUID resourceId, LocalDate date, List<TimeSlot> slots) {
    }

    @Override
    public void evict(UUID resourceId, LocalDate date) {
    }

    @Override
    public void evictResource(UUID resourceId) {
    }
}
