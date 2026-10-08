package com.restaurant.reservation.infrastructure.persistence;

import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VenueAndResourceRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private VenueRepository venues;

    @Autowired
    private ResourceRepository resources;

    private Venue newVenue(UUID ownerId, String name) {
        return venues.save(Venue.create(ownerId, name, VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw")));
    }

    @Test
    void savesAndLoadsVenue() {
        UUID ownerId = insertUser();
        Venue saved = newVenue(ownerId, "Bistro");

        Venue loaded = venues.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Bistro");
        assertThat(loaded.getTimezone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
        assertThat(loaded.getType()).isEqualTo(VenueType.RESTAURANT);
        assertThat(loaded.isOwnedBy(ownerId)).isTrue();
    }

    @Test
    void updatesExistingVenue() {
        Venue saved = newVenue(insertUser(), "Bistro");
        saved.update("Bistro Deluxe", "Second st 2", ZoneId.of("UTC"));
        venues.save(saved);

        Venue loaded = venues.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("Bistro Deluxe");
        assertThat(loaded.getAddress()).isEqualTo("Second st 2");
        assertThat(loaded.getTimezone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void findsVenuesByOwner() {
        UUID firstOwner = insertUser();
        UUID secondOwner = insertUser();
        newVenue(firstOwner, "A");
        newVenue(firstOwner, "B");
        newVenue(secondOwner, "C");

        assertThat(venues.findByOwnerId(firstOwner)).extracting(Venue::getName).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void findsOnlyActiveVenues() {
        UUID ownerId = insertUser();
        newVenue(ownerId, "Active");
        Venue closed = newVenue(ownerId, "Closed");
        closed.deactivate();
        venues.save(closed);

        assertThat(venues.findAllActive()).extracting(Venue::getName).containsExactly("Active");
    }

    @Test
    void savesAndLoadsResourcesOfVenue() {
        Venue venue = newVenue(insertUser(), "Bistro");
        Venue other = newVenue(insertUser(), "Other");
        resources.save(Resource.create(venue.getId(), "Table 1", 4));
        resources.save(Resource.create(venue.getId(), "Table 2", 6));
        resources.save(Resource.create(other.getId(), "Table X", 2));

        assertThat(resources.findByVenueId(venue.getId()))
                .extracting(Resource::getName)
                .containsExactlyInAnyOrder("Table 1", "Table 2");
    }

    @Test
    void keepsResourceCapacityAndActiveFlag() {
        Venue venue = newVenue(insertUser(), "Bistro");
        Resource saved = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        saved.deactivate();
        resources.save(saved);

        Resource loaded = resources.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getCapacity()).isEqualTo(4);
        assertThat(loaded.isActive()).isFalse();
    }
}
