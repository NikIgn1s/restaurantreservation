package com.restaurant.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceEntity {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public ResourceEntity(UUID id, UUID venueId, String name, int capacity, boolean active) {
        this.id = id;
        this.venueId = venueId;
        this.name = name;
        this.capacity = capacity;
        this.active = active;
    }
}
