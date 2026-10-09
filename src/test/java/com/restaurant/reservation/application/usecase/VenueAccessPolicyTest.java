package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueAccessPolicyTest {

    @Mock
    private VenueRepository venues;
    @Mock
    private ResourceRepository resources;

    private VenueAccessPolicy policy;
    private Actor owner;
    private Venue venue;
    private Resource table;

    @BeforeEach
    void setUp() {
        policy = new VenueAccessPolicy(venues, resources);
        owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        venue = Venue.create(owner.id(), "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC"));
        table = Resource.create(venue.getId(), "Table 1", 4);
    }

    @Test
    void ownerManagesOwnVenue() {
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        assertThat(policy.loadManageableVenue(venue.getId(), owner)).isSameAs(venue);
    }

    @Test
    void adminManagesAnyVenue() {
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        Actor admin = new Actor(UUID.randomUUID(), UserRole.ADMIN);

        assertThat(policy.loadManageableVenue(venue.getId(), admin)).isSameAs(venue);
    }

    @Test
    void foreignOwnerIsForbidden() {
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        Actor stranger = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> policy.loadManageableVenue(venue.getId(), stranger))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void unknownVenueIsNotFound() {
        when(venues.findById(venue.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.loadManageableVenue(venue.getId(), owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void loadsResourceOfOwnVenue() {
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        assertThat(policy.loadManageableResource(table.getId(), owner)).isSameAs(table);
    }

    @Test
    void unknownResourceIsNotFound() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.loadManageableResource(table.getId(), owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void canManageResourceOfOwnVenue() {
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        assertThat(policy.canManageResource(table.getId(), owner)).isTrue();
    }

    @Test
    void cannotManageResourceOfForeignVenue() {
        when(resources.findById(table.getId())).thenReturn(Optional.of(table));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));

        assertThat(policy.canManageResource(table.getId(), new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER))).isFalse();
    }

    @Test
    void cannotManageUnknownResource() {
        when(resources.findById(table.getId())).thenReturn(Optional.empty());

        assertThat(policy.canManageResource(table.getId(), owner)).isFalse();
    }

    @Test
    void adminCanManageAnyResourceWithoutLookup() {
        assertThat(policy.canManageResource(table.getId(), new Actor(UUID.randomUUID(), UserRole.ADMIN))).isTrue();
    }
}
