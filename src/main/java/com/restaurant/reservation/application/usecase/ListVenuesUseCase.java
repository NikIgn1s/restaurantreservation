package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListVenuesUseCase {

    private final VenueRepository venues;

    @Transactional(readOnly = true)
    public List<Venue> execute() {
        return venues.findAllActive();
    }
}
