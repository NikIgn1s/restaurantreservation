package com.restaurant.reservation.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Schedule {

    private final UUID id;
    private final UUID resourceId;
    private final DayOfWeek dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private int slotMinutes;

    public Schedule(UUID id, UUID resourceId, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, int slotMinutes) {
        validate(openTime, closeTime, slotMinutes);
        this.id = id;
        this.resourceId = resourceId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotMinutes = slotMinutes;
    }

    public static Schedule create(UUID resourceId, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, int slotMinutes) {
        return new Schedule(UUID.randomUUID(), resourceId, dayOfWeek, openTime, closeTime, slotMinutes);
    }

    public void update(LocalTime openTime, LocalTime closeTime, int slotMinutes) {
        validate(openTime, closeTime, slotMinutes);
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.slotMinutes = slotMinutes;
    }

    public boolean appliesTo(LocalDate date) {
        return date.getDayOfWeek() == dayOfWeek;
    }

    public List<TimeSlot> slotsFor(LocalDate date, ZoneId zone) {
        return generateSlots(date, zone, openTime, closeTime, slotMinutes);
    }

    public List<TimeSlot> slotsFor(ScheduleException exception, ZoneId zone) {
        if (exception.isClosed()) {
            return List.of();
        }
        return generateSlots(exception.getDate(), zone, exception.getOpenTime(), exception.getCloseTime(), slotMinutes);
    }

    private static List<TimeSlot> generateSlots(LocalDate date, ZoneId zone, LocalTime open, LocalTime close, int minutes) {
        List<TimeSlot> slots = new ArrayList<>();
        Duration step = Duration.ofMinutes(minutes);
        var cursor = date.atTime(open).atZone(zone).toInstant();
        var end = date.atTime(close).atZone(zone).toInstant();
        while (!cursor.plus(step).isAfter(end)) {
            slots.add(new TimeSlot(cursor, cursor.plus(step)));
            cursor = cursor.plus(step);
        }
        return slots;
    }

    private static void validate(LocalTime openTime, LocalTime closeTime, int slotMinutes) {
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("Open time must be before close time");
        }
        if (slotMinutes <= 0) {
            throw new IllegalArgumentException("Slot length must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }
}
