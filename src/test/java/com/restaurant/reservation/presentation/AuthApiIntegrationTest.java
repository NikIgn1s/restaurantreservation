package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import com.restaurant.reservation.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;

    private ResultActions register(String email, String password, String role) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Anna Kowalska", "role": "%s"}
                        """.formatted(email, password, role)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    @Test
    void registeredUserCanLogInAndTokenIdentifiesThem() throws Exception {
        String body = register("anna@example.com", "password123", "CUSTOMER")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        String loginBody = login("anna@example.com", "password123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(loginBody);

        assertThat(jwtService.parse(token.get("accessToken").asText())).contains(new Actor(userId, UserRole.CUSTOMER));
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() throws Exception {
        register("anna@example.com", "password123", "CUSTOMER").andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'anna@example.com'", String.class);

        assertThat(stored).startsWith("$2").doesNotContain("password123");
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        register("Anna@Example.COM", "password123", "CUSTOMER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("anna@example.com"));

        login("ANNA@example.com", "password123").andExpect(status().isOk());
        register("anna@example.com", "password123", "CUSTOMER").andExpect(status().isConflict());
    }

    @Test
    void venueOwnerCanRegister() throws Exception {
        register("owner@example.com", "password123", "VENUE_OWNER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VENUE_OWNER"));
    }

    @Test
    void adminCannotBeSelfRegistered() throws Exception {
        register("admin@example.com", "password123", "ADMIN").andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        register("anna@example.com", "password123", "CUSTOMER").andExpect(status().isCreated());

        register("anna@example.com", "another-pass", "VENUE_OWNER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        register("anna@example.com", "short", "CUSTOMER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        register("anna@example.com", "password123", "CUSTOMER").andExpect(status().isCreated());

        String wrongPassword = login("anna@example.com", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = login("ghost@example.com", "password123")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(wrongPassword).get("detail"))
                .isEqualTo(objectMapper.readTree(unknownEmail).get("detail"));
    }

    @Test
    void authEndpointsAreOpenWithoutHeaders() throws Exception {
        register("anna@example.com", "password123", "CUSTOMER").andExpect(status().isCreated());

        login("anna@example.com", "password123").andExpect(status().isOk());
    }

    @Test
    void openApiMarksAuthEndpointsAsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security.length()").value(0))
                .andExpect(jsonPath("$.paths['/api/venues'].post.security").doesNotExist());
    }
}
