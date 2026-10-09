package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateVenueUseCase {

    private final VenueRepository venues;

    @Transactional
    public Venue execute(CreateVenueCommand command) {
        if (!command.actor().canManageVenues()) {
            throw new ForbiddenOperationException("Only venue owners can create venues");
        }
        Venue venue = Venue.create(command.actor().id(), command.name(), command.type(),
                command.address(), command.timezone());
        return venues.save(venue);
    }
}
