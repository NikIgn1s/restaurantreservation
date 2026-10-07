package com.restaurant.reservation.domain.model;

import java.time.ZoneId;
import java.util.UUID;

public class Venue {

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private final VenueType type;
    private String address;
    private ZoneId timezone;
    private boolean active;

    public Venue(UUID id, UUID ownerId, String name, VenueType type, String address, ZoneId timezone, boolean active) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.address = address;
        this.timezone = timezone;
        this.active = active;
    }

    public static Venue create(UUID ownerId, String name, VenueType type, String address, ZoneId timezone) {
        requireName(name);
        return new Venue(UUID.randomUUID(), ownerId, name.trim(), type, address, timezone, true);
    }

    public void update(String name, String address, ZoneId timezone) {
        requireName(name);
        this.name = name.trim();
        this.address = address;
        this.timezone = timezone;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Venue name must not be blank");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public VenueType getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }
}
