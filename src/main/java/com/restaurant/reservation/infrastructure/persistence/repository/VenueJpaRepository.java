package com.restaurant.reservation.infrastructure.persistence.repository;

import com.restaurant.reservation.infrastructure.persistence.entity.VenueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VenueJpaRepository extends JpaRepository<VenueEntity, UUID> {

    List<VenueEntity> findByOwnerId(UUID ownerId);

    List<VenueEntity> findByActiveTrue();
}
