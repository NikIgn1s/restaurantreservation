package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.CreateResourceCommand;
import com.restaurant.reservation.application.dto.CreateVenueCommand;
import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ListVenueResourcesUseCase;
import com.restaurant.reservation.application.usecase.ListVenuesUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.presentation.mapper.VenueApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueController.class)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, VenueApiMapper.class})
class VenueControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_VENUE = """
            {"name": "Bistro", "type": "RESTAURANT", "address": "Main st 1", "timezone": "Europe/Warsaw"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CreateVenueUseCase createVenue;
    @MockBean
    private ListVenuesUseCase listVenues;
    @MockBean
    private CreateResourceUseCase createResource;
    @MockBean
    private ListVenueResourcesUseCase listResources;

    private Venue venue() {
        return Venue.create(OWNER_ID, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw"));
    }

    @Test
    void createsVenueAndReturnsIt() throws Exception {
        Venue venue = venue();
        when(createVenue.execute(any())).thenReturn(venue);

        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VENUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(venue.getId().toString()))
                .andExpect(jsonPath("$.name").value("Bistro"))
                .andExpect(jsonPath("$.type").value("RESTAURANT"))
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void passesActorFromHeadersToUseCase() throws Exception {
        when(createVenue.execute(any())).thenReturn(venue());

        mockMvc.perform(post("/api/venues")
                .header("X-User-Id", OWNER_ID.toString())
                .header("X-User-Role", "venue_owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_VENUE));

        ArgumentCaptor<CreateVenueCommand> captor = ArgumentCaptor.forClass(CreateVenueCommand.class);
        verify(createVenue).execute(captor.capture());
        assertThat(captor.getValue().actor().id()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().actor().role()).isEqualTo(UserRole.VENUE_OWNER);
        assertThat(captor.getValue().timezone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
    }

    @Test
    void requiresAuthenticationHeaders() throws Exception {
        mockMvc.perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(VALID_VENUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));

        verifyNoInteractions(createVenue);
    }

    @Test
    void rejectsUnknownRoleHeader() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "SUPERUSER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VENUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedUserIdHeader() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VENUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  \", \"timezone\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.timezone").exists());

        verify(createVenue, never()).execute(any());
    }

    @Test
    void rejectsUnknownTimezone() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bistro\", \"type\": \"RESTAURANT\", \"timezone\": \"Mars/Base\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(createVenue);
    }

    @Test
    void rejectsUnknownVenueType() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bistro\", \"type\": \"CASTLE\", \"timezone\": \"UTC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void translatesForbiddenFromUseCase() throws Exception {
        when(createVenue.execute(any())).thenThrow(new ForbiddenOperationException("Only venue owners can create venues"));

        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_VENUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only venue owners can create venues"));
    }

    @Test
    void listsVenuesWithoutAuthentication() throws Exception {
        when(listVenues.execute()).thenReturn(List.of(venue()));

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bistro"));
    }

    @Test
    void createsResourceInVenue() throws Exception {
        UUID venueId = UUID.randomUUID();
        Resource resource = Resource.create(venueId, "Table 1", 4);
        when(createResource.execute(any())).thenReturn(resource);

        mockMvc.perform(post("/api/venues/{id}/resources", venueId)
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Table 1\", \"capacity\": 4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.capacity").value(4));

        ArgumentCaptor<CreateResourceCommand> captor = ArgumentCaptor.forClass(CreateResourceCommand.class);
        verify(createResource).execute(captor.capture());
        assertThat(captor.getValue().venueId()).isEqualTo(venueId);
        assertThat(captor.getValue().name()).isEqualTo("Table 1");
    }

    @Test
    void rejectsNonPositiveCapacity() throws Exception {
        mockMvc.perform(post("/api/venues/{id}/resources", UUID.randomUUID())
                        .header("X-User-Id", OWNER_ID.toString())
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Table 1\", \"capacity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").exists());
    }

    @Test
    void rejectsMalformedVenueIdInPath() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/resources", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsResourcesOfVenue() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(listResources.execute(venueId)).thenReturn(List.of(Resource.create(venueId, "Table 1", 4)));

        mockMvc.perform(get("/api/venues/{id}/resources", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Table 1"));
    }

    @Test
    void unknownVenueGivesNotFound() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(listResources.execute(venueId)).thenThrow(new NotFoundException("Venue " + venueId + " not found"));

        mockMvc.perform(get("/api/venues/{id}/resources", venueId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
