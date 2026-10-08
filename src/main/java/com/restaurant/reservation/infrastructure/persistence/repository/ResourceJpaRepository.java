package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.infrastructure.persistence.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceJpaRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByVenueId(UUID venueId);
}
