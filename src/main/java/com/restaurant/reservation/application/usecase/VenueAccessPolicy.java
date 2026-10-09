package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VenueAccessPolicy {

    private final VenueRepository venues;
    private final ResourceRepository resources;

    public Venue loadManageableVenue(UUID venueId, Actor actor) {
        Venue venue = venues.findById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue " + venueId + " not found"));
        if (!actor.isAdmin() && !venue.isOwnedBy(actor.id())) {
            throw new ForbiddenOperationException("Venue belongs to another owner");
        }
        return venue;
    }

    public Resource loadManageableResource(UUID resourceId, Actor actor) {
        Resource resource = resources.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource " + resourceId + " not found"));
        loadManageableVenue(resource.getVenueId(), actor);
        return resource;
    }
}
