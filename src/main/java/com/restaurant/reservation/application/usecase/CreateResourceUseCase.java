package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateResourceUseCase {

    private final VenueAccessPolicy accessPolicy;
    private final ResourceRepository resources;

    @Transactional
    public Resource execute(CreateResourceCommand command) {
        accessPolicy.loadManageableVenue(command.venueId(), command.actor());
        Resource resource = Resource.create(command.venueId(), command.name(), command.capacity());
        return resources.save(resource);
    }
}
