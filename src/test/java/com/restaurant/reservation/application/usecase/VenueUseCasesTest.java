package com.restaurant.reservation.application.usecase;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueUseCasesTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Mock
    private VenueRepository venues;
    @Mock
    private ResourceRepository resources;
    @Mock
    private VenueAccessPolicy accessPolicy;

    @Test
    void ownerCreatesVenueOwnedByHimself() {
        Actor owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        when(venues.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Venue venue = new CreateVenueUseCase(venues)
                .execute(new CreateVenueCommand(owner, "Bistro", VenueType.RESTAURANT, "Main st 1", WARSAW));

        ArgumentCaptor<Venue> saved = ArgumentCaptor.forClass(Venue.class);
        verify(venues).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(venue);
        assertThat(venue.isOwnedBy(owner.id())).isTrue();
        assertThat(venue.getTimezone()).isEqualTo(WARSAW);
        assertThat(venue.isActive()).isTrue();
    }

    @Test
    void adminMayCreateVenue() {
        when(venues.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Venue venue = new CreateVenueUseCase(venues).execute(new CreateVenueCommand(
                new Actor(UUID.randomUUID(), UserRole.ADMIN), "Gym", VenueType.SPORT, "Park st 2", WARSAW));

        assertThat(venue.getType()).isEqualTo(VenueType.SPORT);
    }

    @Test
    void customerCannotCreateVenue() {
        Actor customer = new Actor(UUID.randomUUID(), UserRole.CUSTOMER);

        assertThatThrownBy(() -> new CreateVenueUseCase(venues)
                .execute(new CreateVenueCommand(customer, "Hack", VenueType.RESTAURANT, "x", WARSAW)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(venues);
    }

    @Test
    void blankVenueNameIsRejected() {
        Actor owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);

        assertThatThrownBy(() -> new CreateVenueUseCase(venues)
                .execute(new CreateVenueCommand(owner, "  ", VenueType.RESTAURANT, "x", WARSAW)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(venues, never()).save(any());
    }

    @Test
    void ownerCreatesResourceInOwnVenue() {
        Actor owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        Venue venue = Venue.create(owner.id(), "Bistro", VenueType.RESTAURANT, "Main st 1", WARSAW);
        when(accessPolicy.loadManageableVenue(venue.getId(), owner)).thenReturn(venue);
        when(resources.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Resource resource = new CreateResourceUseCase(accessPolicy, resources)
                .execute(new CreateResourceCommand(owner, venue.getId(), "Table 1", 4));

        assertThat(resource.getVenueId()).isEqualTo(venue.getId());
        assertThat(resource.getCapacity()).isEqualTo(4);
        assertThat(resource.isActive()).isTrue();
    }

    @Test
    void resourceIsNotSavedWhenAccessIsDenied() {
        Actor stranger = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        UUID venueId = UUID.randomUUID();
        when(accessPolicy.loadManageableVenue(venueId, stranger)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> new CreateResourceUseCase(accessPolicy, resources)
                .execute(new CreateResourceCommand(stranger, venueId, "Table 1", 4)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(resources);
    }

    @Test
    void nonPositiveCapacityIsRejected() {
        Actor owner = new Actor(UUID.randomUUID(), UserRole.VENUE_OWNER);
        UUID venueId = UUID.randomUUID();
        when(accessPolicy.loadManageableVenue(venueId, owner))
                .thenReturn(Venue.create(owner.id(), "Bistro", VenueType.RESTAURANT, "x", WARSAW));

        assertThatThrownBy(() -> new CreateResourceUseCase(accessPolicy, resources)
                .execute(new CreateResourceCommand(owner, venueId, "Table 1", 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsOnlyActiveVenues() {
        Venue venue = Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "x", WARSAW);
        when(venues.findAllActive()).thenReturn(List.of(venue));

        assertThat(new ListVenuesUseCase(venues).execute()).containsExactly(venue);
    }

    @Test
    void listsOnlyActiveResourcesOfVenue() {
        UUID venueId = UUID.randomUUID();
        Resource visible = Resource.create(venueId, "Table 1", 4);
        Resource hidden = Resource.create(venueId, "Table 2", 4);
        hidden.deactivate();
        when(venues.findById(venueId))
                .thenReturn(Optional.of(Venue.create(UUID.randomUUID(), "Bistro", VenueType.RESTAURANT, "x", WARSAW)));
        when(resources.findByVenueId(venueId)).thenReturn(List.of(visible, hidden));

        assertThat(new ListVenueResourcesUseCase(venues, resources).execute(venueId)).containsExactly(visible);
    }

    @Test
    void listingResourcesOfUnknownVenueFails() {
        UUID venueId = UUID.randomUUID();
        when(venues.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new ListVenueResourcesUseCase(venues, resources).execute(venueId))
                .isInstanceOf(NotFoundException.class);
    }
}
