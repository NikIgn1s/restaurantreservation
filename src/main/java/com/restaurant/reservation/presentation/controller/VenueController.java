package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ListVenueResourcesUseCase;
import com.restaurant.reservation.application.usecase.ListVenuesUseCase;
import com.restaurant.reservation.presentation.mapper.VenueApiMapper;
import com.restaurant.reservation.presentation.request.CreateResourceRequest;
import com.restaurant.reservation.presentation.request.CreateVenueRequest;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.ResourceResponse;
import com.restaurant.reservation.presentation.response.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Venues", description = "Venues and their bookable resources")
public class VenueController {

    private final CreateVenueUseCase createVenue;
    private final ListVenuesUseCase listVenues;
    private final CreateResourceUseCase createResource;
    private final ListVenueResourcesUseCase listResources;
    private final VenueApiMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a venue", description = "Allowed for VENUE_OWNER and ADMIN")
    public VenueResponse createVenue(@CurrentActor Actor actor, @Valid @RequestBody CreateVenueRequest request) {
        return mapper.toResponse(createVenue.execute(mapper.toCommand(actor, request)));
    }

    @GetMapping
    @Operation(summary = "List active venues")
    public List<VenueResponse> listVenues() {
        return listVenues.execute().stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/{venueId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a table, room or court to a venue", description = "Allowed for the venue owner and ADMIN")
    public ResourceResponse createResource(@CurrentActor Actor actor, @PathVariable UUID venueId,
                                           @Valid @RequestBody CreateResourceRequest request) {
        return mapper.toResponse(createResource.execute(mapper.toCommand(actor, venueId, request)));
    }

    @GetMapping("/{venueId}/resources")
    @Operation(summary = "List active resources of a venue")
    public List<ResourceResponse> listResources(@PathVariable UUID venueId) {
        return listResources.execute(venueId).stream().map(mapper::toResponse).toList();
    }
}
