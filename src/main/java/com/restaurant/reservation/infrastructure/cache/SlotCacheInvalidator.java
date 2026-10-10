package com.restaurant.reservation.infrastructure.cache;

import com.restaurant.reservation.application.event.ResourceSlotsChanged;
import com.restaurant.reservation.application.event.VenueSlotsChanged;
import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SlotCacheInvalidator {

    private final SlotCache cache;
    private final ResourceRepository resources;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResourceChanged(ResourceSlotsChanged event) {
        cache.evictResource(event.resourceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVenueChanged(VenueSlotsChanged event) {
        resources.findByVenueId(event.venueId()).forEach(resource -> cache.evictResource(resource.getId()));
    }
}
