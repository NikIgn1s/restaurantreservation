package com.restaurant.reservation.domain.model;

import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;

import java.time.Instant;
import java.util.UUID;

public class Booking {

    private final UUID id;
    private final UUID resourceId;
    private final UUID userId;
    private final TimeSlot slot;
    private final int guests;
    private BookingStatus status;
    private boolean depositRequired;
    private Double noShowProbability;
    private final Instant createdAt;
    private Instant reminderSentAt;
    private boolean overbooked;
    private final long version;

    public Booking(UUID id, UUID resourceId, UUID userId, TimeSlot slot, int guests, BookingStatus status,
                   boolean depositRequired, Double noShowProbability, Instant createdAt, Instant reminderSentAt,
                   boolean overbooked, long version) {
        this.id = id;
        this.resourceId = resourceId;
        this.userId = userId;
        this.slot = slot;
        this.guests = guests;
        this.status = status;
        this.depositRequired = depositRequired;
        this.noShowProbability = noShowProbability;
        this.createdAt = createdAt;
        this.reminderSentAt = reminderSentAt;
        this.overbooked = overbooked;
        this.version = version;
    }

    public static Booking create(Resource resource, UUID userId, TimeSlot slot, int guests, Instant now) {
        if (!resource.isActive()) {
            throw new InvalidBookingException("Resource is not available for booking");
        }
        if (!resource.fits(guests)) {
            throw new InvalidBookingException("Guests count exceeds resource capacity");
        }
        if (slot.startsBefore(now)) {
            throw new InvalidBookingException("Booking cannot start in the past");
        }
        return new Booking(UUID.randomUUID(), resource.getId(), userId, slot, guests,
                BookingStatus.PENDING, false, null, now, null, false, 0L);
    }

    public void assessNoShowRisk(double probability, double depositThreshold) {
        this.noShowProbability = probability;
        this.depositRequired = probability > depositThreshold;
    }

    public void confirm() {
        transitionTo(BookingStatus.CONFIRMED);
    }

    public void cancel() {
        transitionTo(BookingStatus.CANCELLED);
    }

    public void expire() {
        transitionTo(BookingStatus.EXPIRED);
    }

    public void complete() {
        transitionTo(BookingStatus.COMPLETED);
    }

    public void markNoShow() {
        transitionTo(BookingStatus.NO_SHOW);
    }

    public boolean isOverbookable(double minProbability) {
        return !overbooked && status == BookingStatus.PENDING && depositRequired
                && noShowProbability != null && noShowProbability >= minProbability;
    }

    public void markOverbooked() {
        if (status != BookingStatus.PENDING) {
            throw new InvalidBookingException("Only a pending booking can be placed behind another one");
        }
        this.overbooked = true;
    }

    public void promote() {
        if (!overbooked || !status.occupiesSlot()) {
            throw new InvalidBookingException("Only an active standby booking can be promoted");
        }
        this.overbooked = false;
    }

    public boolean needsReminder() {
        return status == BookingStatus.CONFIRMED && reminderSentAt == null;
    }

    public void markReminderSent(Instant when) {
        if (status != BookingStatus.CONFIRMED) {
            throw new InvalidBookingException("Reminder can be sent only for a confirmed booking");
        }
        this.reminderSentAt = when;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    private void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidBookingStateException(status, target);
        }
        this.status = target;
    }

    public UUID getId() {
        return id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public int getGuests() {
        return guests;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public boolean isDepositRequired() {
        return depositRequired;
    }

    public Double getNoShowProbability() {
        return noShowProbability;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }

    public boolean isOverbooked() {
        return overbooked;
    }

    public long getVersion() {
        return version;
    }
}
