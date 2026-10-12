package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverbookingCoordinator {

    private final BookingRepository bookings;

    public void onSlotFreed(Booking freed) {
        if (freed.isOverbooked() || freed.getStatus().occupiesSlot()) {
            return;
        }
        bookings.findActiveByResourceAndSlot(freed.getResourceId(), freed.getSlot()).stream()
                .filter(Booking::isOverbooked)
                .findFirst()
                .ifPresent(standby -> {
                    standby.promote();
                    bookings.save(standby);
                    log.info("Standby booking {} took over the slot of booking {}", standby.getId(), freed.getId());
                });
    }

    public void onSecured(Booking secured) {
        if (secured.isOverbooked()) {
            return;
        }
        bookings.findActiveByResourceAndSlot(secured.getResourceId(), secured.getSlot()).stream()
                .filter(Booking::isOverbooked)
                .forEach(standby -> {
                    standby.cancel();
                    bookings.save(standby);
                    log.info("Standby booking {} was cancelled because booking {} is confirmed", standby.getId(), secured.getId());
                });
    }
}
