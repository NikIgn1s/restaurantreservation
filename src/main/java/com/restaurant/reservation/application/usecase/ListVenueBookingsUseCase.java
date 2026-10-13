package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.VenueBookingsQuery;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListVenueBookingsUseCase {

    private final VenueAccessPolicy accessPolicy;
    private final BookingRepository bookings;

    @Transactional(readOnly = true)
    public List<Booking> execute(VenueBookingsQuery query) {
        Venue venue = accessPolicy.loadManageableVenue(query.venueId(), query.actor());
        ZoneId zone = venue.getTimezone();
        Instant from = query.date().atStartOfDay(zone).toInstant();
        Instant to = query.date().plusDays(1).atStartOfDay(zone).toInstant();
        return bookings.findByVenueAndPeriod(venue.getId(), from, to);
    }
}
