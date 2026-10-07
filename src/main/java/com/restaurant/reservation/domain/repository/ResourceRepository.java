package com.restaurant.reservation.domain.repository;

import com.restaurant.reservation.domain.model.Resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceRepository {

    Resource save(Resource resource);

    Optional<Resource> findById(UUID id);

    List<Resource> findByVenueId(UUID venueId);
}
