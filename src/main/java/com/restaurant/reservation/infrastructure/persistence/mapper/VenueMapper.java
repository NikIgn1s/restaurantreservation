package com.restaurant.reservation.infrastructure.persistence.mapper;

import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.infrastructure.persistence.entity.VenueEntity;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class VenueMapper {

    public Venue toDomain(VenueEntity entity) {
        return new Venue(entity.getId(), entity.getOwnerId(), entity.getName(), entity.getType(),
                entity.getAddress(), ZoneId.of(entity.getTimezone()), entity.isActive());
    }

    public VenueEntity toEntity(Venue venue) {
        return new VenueEntity(venue.getId(), venue.getOwnerId(), venue.getName(), venue.getType(),
                venue.getAddress(), venue.getTimezone().getId(), venue.isActive());
    }
}
