package com.restaurant.reservation.presentation;

import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class HealthEndpointIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsOpenAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthDoesNotLeakComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        String token = bearer(UUID.randomUUID(), UserRole.ADMIN);

        mockMvc.perform(get("/actuator/env").header("Authorization", token)).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header("Authorization", token)).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/heapdump").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void otherActuatorEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/actuator/health")).andExpect(status().isUnauthorized());
    }
}
