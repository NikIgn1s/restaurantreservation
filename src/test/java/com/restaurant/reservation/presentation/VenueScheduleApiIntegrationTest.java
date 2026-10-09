package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class VenueScheduleApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID ownerId;

    @BeforeEach
    void createOwner() {
        ownerId = insertUser();
    }

    private MockHttpServletRequestBuilder asOwner(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", ownerId.toString()).header("X-User-Role", "VENUE_OWNER")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String createVenue() throws Exception {
        String body = mockMvc.perform(asOwner(post("/api/venues"))
                        .content("{\"name\": \"Bistro\", \"type\": \"RESTAURANT\", \"address\": \"Main st 1\", \"timezone\": \"Europe/Warsaw\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String createResource(String venueId) throws Exception {
        String body = mockMvc.perform(asOwner(post("/api/venues/{id}/resources", venueId))
                        .content("{\"name\": \"Table 1\", \"capacity\": 4}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void ownerBuildsVenueWithResourceAndSchedule() throws Exception {
        String venueId = createVenue();
        String resourceId = createResource(venueId);

        mockMvc.perform(asOwner(put("/api/resources/{id}/schedule/MONDAY", resourceId))
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"));
        mockMvc.perform(asOwner(put("/api/resources/{id}/schedule/exceptions/2030-12-25", resourceId))
                        .content("{\"closed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(venueId))
                .andExpect(jsonPath("$[0].ownerId").value(ownerId.toString()));
        mockMvc.perform(get("/api/venues/{id}/resources", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(resourceId))
                .andExpect(jsonPath("$[0].capacity").value(4));
        mockMvc.perform(get("/api/resources/{id}/schedule", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].slotMinutes").value(60));
    }

    @Test
    void settingTheSameWeekdayTwiceReplacesIt() throws Exception {
        String resourceId = createResource(createVenue());
        String path = "/api/resources/{id}/schedule/TUESDAY";

        mockMvc.perform(asOwner(put(path, resourceId))
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isOk());
        mockMvc.perform(asOwner(put(path, resourceId))
                        .content("{\"openTime\": \"12:00\", \"closeTime\": \"18:00\", \"slotMinutes\": 30}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/resources/{id}/schedule", resourceId))
                .andReturn().getResponse().getContentAsString();
        JsonNode schedule = objectMapper.readTree(body);
        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).get("slotMinutes").asInt()).isEqualTo(30);
    }

    @Test
    void customerCannotCreateVenue() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", insertUser().toString())
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hack\", \"type\": \"RESTAURANT\", \"timezone\": \"UTC\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignOwnerCannotAddResourceOrChangeSchedule() throws Exception {
        String venueId = createVenue();
        String resourceId = createResource(venueId);
        String stranger = insertUser().toString();

        mockMvc.perform(post("/api/venues/{id}/resources", venueId)
                        .header("X-User-Id", stranger)
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Table 2\", \"capacity\": 2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/resources/{id}/schedule/MONDAY", resourceId)
                        .header("X-User-Id", stranger)
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownVenueReturnsProblemDetail() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/resources", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void openApiDocumentDescribesTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Reservation API"))
                .andExpect(jsonPath("$.paths['/api/venues']").exists())
                .andExpect(jsonPath("$.paths['/api/resources/{resourceId}/schedule/{dayOfWeek}']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.userId.name").value("X-User-Id"));
    }
}
