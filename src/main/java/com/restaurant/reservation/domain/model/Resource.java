package com.restaurant.reservation.domain.model;

import java.util.UUID;

public class Resource {

    private final UUID id;
    private final UUID venueId;
    private String name;
    private int capacity;
    private boolean active;

    public Resource(UUID id, UUID venueId, String name, int capacity, boolean active) {
        this.id = id;
        this.venueId = venueId;
        this.name = name;
        this.capacity = capacity;
        this.active = active;
    }

    public static Resource create(UUID venueId, String name, int capacity) {
        validate(name, capacity);
        return new Resource(UUID.randomUUID(), venueId, name.trim(), capacity, true);
    }

    public void update(String name, int capacity) {
        validate(name, capacity);
        this.name = name.trim();
        this.capacity = capacity;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean fits(int guests) {
        return guests > 0 && guests <= capacity;
    }

    private static void validate(String name, int capacity) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Resource capacity must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }
}
