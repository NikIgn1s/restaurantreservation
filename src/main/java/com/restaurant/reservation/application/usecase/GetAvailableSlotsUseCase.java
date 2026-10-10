package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.port.SlotCache;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.domain.service.SlotAvailabilityCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetAvailableSlotsUseCase {

    private final ResourceRepository resources;
    private final VenueRepository venues;
    private final ScheduleRepository schedules;
    private final BookingRepository bookings;
    private final SlotCache cache;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TimeSlot> execute(AvailableSlotsQuery query) {
        Optional<List<TimeSlot>> cached = cache.get(query.resourceId(), query.date());
        if (cached.isPresent()) {
            return upcoming(cached.get());
        }
        List<TimeSlot> free = loadFreeSlots(query);
        cache.put(query.resourceId(), query.date(), free);
        return upcoming(free);
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> executeFresh(AvailableSlotsQuery query) {
        return upcoming(loadFreeSlots(query));
    }

    private List<TimeSlot> upcoming(List<TimeSlot> slots) {
        Instant now = clock.instant();
        return slots.stream().filter(slot -> !slot.startsBefore(now)).toList();
    }

    private List<TimeSlot> loadFreeSlots(AvailableSlotsQuery query) {
        Resource resource = resources.findById(query.resourceId())
                .orElseThrow(() -> new NotFoundException("Resource " + query.resourceId() + " not found"));
        Venue venue = venues.findById(resource.getVenueId())
                .orElseThrow(() -> new NotFoundException("Venue " + resource.getVenueId() + " not found"));
        if (!resource.isActive() || !venue.isActive()) {
            return List.of();
        }

        Optional<Schedule> weekly = schedules.findByResourceId(resource.getId()).stream()
                .filter(schedule -> schedule.appliesTo(query.date()))
                .findFirst();
        if (weekly.isEmpty()) {
            return List.of();
        }

        ZoneId zone = venue.getTimezone();
        Schedule schedule = weekly.get();
        List<TimeSlot> candidates = schedules.findException(resource.getId(), query.date())
                .map(exception -> schedule.slotsFor(exception, zone))
                .orElseGet(() -> schedule.slotsFor(query.date(), zone));
        if (candidates.isEmpty()) {
            return List.of();
        }

        TimeSlot window = new TimeSlot(candidates.get(0).start(), candidates.get(candidates.size() - 1).end());
        List<Booking> active = bookings.findActiveByResourceAndSlot(resource.getId(), window);
        return SlotAvailabilityCalculator.freeSlots(candidates, active, Instant.MIN);
    }
}
