package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidTimeSlotException;

import java.time.Duration;
import java.time.Instant;

public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new InvalidTimeSlotException("Slot boundaries must not be null");
        }
        if (!start.isBefore(end)) {
            throw new InvalidTimeSlotException("Slot start must be before end");
        }
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public boolean startsBefore(Instant moment) {
        return start.isBefore(moment);
    }
}
