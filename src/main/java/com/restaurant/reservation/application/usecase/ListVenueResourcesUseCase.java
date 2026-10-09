package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListVenueResourcesUseCase {

    private final VenueRepository venues;
    private final ResourceRepository resources;

    @Transactional(readOnly = true)
    public List<Resource> execute(UUID venueId) {
        if (venues.findById(venueId).isEmpty()) {
            throw new NotFoundException("Venue " + venueId + " not found");
        }
        return resources.findByVenueId(venueId).stream()
                .filter(Resource::isActive)
                .toList();
    }
}
