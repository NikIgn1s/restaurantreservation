package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Venue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository {

    Venue save(Venue venue);

    Optional<Venue> findById(UUID id);

    List<Venue> findByOwnerId(UUID ownerId);

    List<Venue> findAllActive();
}
