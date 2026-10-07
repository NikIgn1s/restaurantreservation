package com.restaurant.reservation.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class ScheduleException {

    private final UUID id;
    private final UUID resourceId;
    private final LocalDate date;
    private final boolean closed;
    private final LocalTime openTime;
    private final LocalTime closeTime;

    public ScheduleException(UUID id, UUID resourceId, LocalDate date, boolean closed, LocalTime openTime, LocalTime closeTime) {
        if (!closed && (openTime == null || closeTime == null || !openTime.isBefore(closeTime))) {
            throw new IllegalArgumentException("Open time must be before close time");
        }
        this.id = id;
        this.resourceId = resourceId;
        this.date = date;
        this.closed = closed;
        this.openTime = closed ? null : openTime;
        this.closeTime = closed ? null : closeTime;
    }

    public static ScheduleException closedDay(UUID resourceId, LocalDate date) {
        return new ScheduleException(UUID.randomUUID(), resourceId, date, true, null, null);
    }

    public static ScheduleException specialHours(UUID resourceId, LocalDate date, LocalTime openTime, LocalTime closeTime) {
        return new ScheduleException(UUID.randomUUID(), resourceId, date, false, openTime, closeTime);
    }

    public UUID getId() {
        return id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isClosed() {
        return closed;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
