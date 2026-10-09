package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.domain.model.User;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import com.restaurant.reservation.infrastructure.security.JwtProperties;
import com.restaurant.reservation.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String VENUE = "{\"name\": \"Bistro\", \"type\": \"RESTAURANT\", \"timezone\": \"UTC\"}";
    private static final String SCHEDULE = "{\"openTime\": \"10:00\", \"closeTime\": \"22:00\", \"slotMinutes\": 60}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtProperties jwtProperties;

    private ResultActions createVenue(String authorization) throws Exception {
        var request = post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(VENUE);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request);
    }

    private String tokenIssuedAt(Instant instant, UUID userId, UserRole role) {
        JwtService past = new JwtService(jwtProperties, Clock.fixed(instant, ZoneOffset.UTC));
        return "Bearer " + past.issue(new User(userId, "x@test.com", "hash", "X", role, instant)).value();
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        createVenue(null)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/venues"));
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        createVenue("Bearer not.a.token").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSchemeIsUnauthorized() throws Exception {
        createVenue("Basic dXNlcjpwYXNz").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String expired = tokenIssuedAt(Instant.now().minus(Duration.ofHours(3)), insertUser(), UserRole.VENUE_OWNER);

        createVenue(expired).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsUnauthorized() throws Exception {
        JwtService foreign = new JwtService(
                new JwtProperties("foreign-secret-foreign-secret-foreign-1", Duration.ofHours(1), jwtProperties.issuer()),
                Clock.systemUTC());
        String token = foreign.issue(new User(UUID.randomUUID(), "x@test.com", "hash", "X", UserRole.ADMIN, Instant.now())).value();

        createVenue("Bearer " + token).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyIdentityHeadersNoLongerAuthenticate() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", insertUser().toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void venueOwnerWithTokenCreatesVenue() throws Exception {
        createVenue(bearer(insertUser(), UserRole.VENUE_OWNER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bistro"));
    }

    @Test
    void lowercaseBearerSchemeIsAccepted() throws Exception {
        createVenue(bearer(insertUser(), UserRole.VENUE_OWNER).replace("Bearer", "bearer"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminWithTokenCreatesVenue() throws Exception {
        createVenue(bearer(insertUser(), UserRole.ADMIN)).andExpect(status().isCreated());
    }

    @Test
    void customerCannotCreateVenue() throws Exception {
        createVenue(bearer(insertUser(), UserRole.CUSTOMER))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void customerCannotManageResourcesOrSchedules() throws Exception {
        String customer = bearer(insertUser(), UserRole.CUSTOMER);
        UUID anyId = UUID.randomUUID();

        mockMvc.perform(post("/api/venues/{id}/resources", anyId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"T\", \"capacity\": 2}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/resources/{id}/schedule/MONDAY", anyId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content(SCHEDULE))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/resources/{id}/schedule/exceptions/2030-12-25", anyId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closed\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanUseBookingEndpoints() throws Exception {
        String customer = bearer(insertUser(), UserRole.CUSTOMER);

        mockMvc.perform(get("/api/bookings/me").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bookingEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/{id}/cancel", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void publicReadEndpointsWorkWithoutToken() throws Exception {
        mockMvc.perform(get("/api/venues")).andExpect(status().isOk());
        mockMvc.perform(get("/api/venues/{id}/resources", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resources/{id}/schedule", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resources/{id}/slots", UUID.randomUUID()).param("date", "2030-01-07"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTokenOnPublicEndpointIsIgnored() throws Exception {
        mockMvc.perform(get("/api/venues").header("Authorization", "Bearer garbage")).andExpect(status().isOk());
    }

    @Test
    void swaggerAndApiDocsAreOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    @Test
    void unknownPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/venues")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserEndpointReturnsProfileOfTokenOwner() throws Exception {
        UUID userId = insertUser();

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(userId, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(userId + "@test.com"));
    }

    @Test
    void currentUserEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfDeletedUserGetsNotFoundOnProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(UUID.randomUUID(), UserRole.CUSTOMER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullFlowRegisterLoginAndCreateVenueWithIssuedToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"owner@example.com\", \"password\": \"password123\", \"fullName\": \"Olga\", \"role\": \"VENUE_OWNER\"}"))
                .andExpect(status().isCreated());
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"owner@example.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginBody).get("accessToken").asText();

        createVenue("Bearer " + token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").exists());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("VENUE_OWNER"));
    }

    @Test
    void registeredCustomerCannotCreateVenueWithOwnToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"cust@example.com\", \"password\": \"password123\", \"fullName\": \"Cust\", \"role\": \"CUSTOMER\"}"))
                .andExpect(status().isCreated());
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"cust@example.com\", \"password\": \"password123\"}"))
                .andReturn().getResponse().getContentAsString();

        createVenue("Bearer " + objectMapper.readTree(loginBody).get("accessToken").asText())
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDeclaresBearerSchemeAndKeepsPublicEndpointsOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/venues'].get.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/resources/{resourceId}/slots'].get.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/bookings'].post.security").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.userId").doesNotExist());
    }
}
