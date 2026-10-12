package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.policy.RiskPolicy;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.service.OverbookingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetOverbookableSlotsUseCase {

    private final GetAvailableSlotsUseCase availableSlots;
    private final BookingRepository bookings;
    private final RiskPolicy riskPolicy;

    @Transactional(readOnly = true)
    public List<TimeSlot> execute(AvailableSlotsQuery query) {
        List<TimeSlot> scheduled = availableSlots.scheduledSlots(query);
        if (!riskPolicy.overbookingEnabled() || scheduled.isEmpty()) {
            return List.of();
        }
        TimeSlot window = new TimeSlot(scheduled.get(0).start(), scheduled.get(scheduled.size() - 1).end());
        List<Booking> active = bookings.findActiveByResourceAndSlot(query.resourceId(), window);
        return scheduled.stream()
                .filter(slot -> OverbookingPolicy.allows(overlapping(active, slot), riskPolicy.overbookingMinProbability()))
                .toList();
    }

    private static List<Booking> overlapping(List<Booking> active, TimeSlot slot) {
        return active.stream().filter(booking -> booking.getSlot().overlaps(slot)).toList();
    }
}
