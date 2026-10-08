package com.restaurant.reservation.infrastructure.persistence.entity;

import com.restaurant.reservation.domain.model.VenueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VenueType type;

    @Column(length = 512)
    private String address;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public VenueEntity(UUID id, UUID ownerId, String name, VenueType type, String address, String timezone, boolean active) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.address = address;
        this.timezone = timezone;
        this.active = active;
    }
}
