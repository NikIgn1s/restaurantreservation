package com.restaurant.reservation.presentation.mapper;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.presentation.request.CreateResourceRequest;
import com.restaurant.reservation.presentation.request.CreateVenueRequest;
import com.restaurant.reservation.presentation.response.ResourceResponse;
import com.restaurant.reservation.presentation.response.VenueResponse;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class VenueApiMapper {

    public CreateVenueCommand toCommand(Actor actor, CreateVenueRequest request) {
        return new CreateVenueCommand(actor, request.name(), request.type(), request.address(), parseZone(request.timezone()));
    }

    public CreateResourceCommand toCommand(Actor actor, UUID venueId, CreateResourceRequest request) {
        return new CreateResourceCommand(actor, venueId, request.name(), request.capacity());
    }

    public VenueResponse toResponse(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getOwnerId(), venue.getName(), venue.getType(),
                venue.getAddress(), venue.getTimezone().getId(), venue.isActive());
    }

    public ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getVenueId(), resource.getName(),
                resource.getCapacity(), resource.isActive());
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
    }
}
