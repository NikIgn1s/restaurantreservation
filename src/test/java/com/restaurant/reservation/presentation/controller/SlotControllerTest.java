package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.application.usecase.GetOverbookableSlotsUseCase;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SlotController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, BookingApiMapper.class})
class SlotControllerTest {

    private static final UUID RESOURCE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final LocalDate DATE = LocalDate.of(2030, 1, 7);

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private GetAvailableSlotsUseCase availableSlots;
    @MockBean
    private GetOverbookableSlotsUseCase overbookableSlots;

    @Test
    void returnsFreeSlotsWithoutAuthentication() throws Exception {
        when(availableSlots.execute(new AvailableSlotsQuery(RESOURCE_ID, DATE))).thenReturn(List.of(
                new TimeSlot(Instant.parse("2030-01-07T09:00:00Z"), Instant.parse("2030-01-07T10:00:00Z")),
                new TimeSlot(Instant.parse("2030-01-07T10:00:00Z"), Instant.parse("2030-01-07T11:00:00Z"))));

        mockMvc.perform(get("/api/resources/{id}/slots", RESOURCE_ID).param("date", "2030-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-07T09:00:00Z"))
                .andExpect(jsonPath("$[1].endAt").value("2030-01-07T11:00:00Z"));
    }

    @Test
    void returnsEmptyListForClosedDay() throws Exception {
        when(availableSlots.execute(new AvailableSlotsQuery(RESOURCE_ID, DATE))).thenReturn(List.of());

        mockMvc.perform(get("/api/resources/{id}/slots", RESOURCE_ID).param("date", "2030-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsOverbookableSlots() throws Exception {
        when(overbookableSlots.execute(new AvailableSlotsQuery(RESOURCE_ID, DATE))).thenReturn(List.of(
                new TimeSlot(Instant.parse("2030-01-07T11:00:00Z"), Instant.parse("2030-01-07T12:00:00Z"))));

        mockMvc.perform(get("/api/resources/{id}/slots/overbookable", RESOURCE_ID).param("date", "2030-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-07T11:00:00Z"));
    }

    @Test
    void overbookableSlotsNeedADate() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/slots/overbookable", RESOURCE_ID)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownResourceHasNoOverbookableSlots() throws Exception {
        when(overbookableSlots.execute(new AvailableSlotsQuery(RESOURCE_ID, DATE)))
                .thenThrow(new NotFoundException("Resource " + RESOURCE_ID + " not found"));

        mockMvc.perform(get("/api/resources/{id}/slots/overbookable", RESOURCE_ID).param("date", "2030-01-07"))
                .andExpect(status().isNotFound());
    }

    @Test
    void dateParameterIsRequired() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/slots", RESOURCE_ID)).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/slots", RESOURCE_ID).param("date", "07.01.2030"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownResourceIsNotFound() throws Exception {
        when(availableSlots.execute(new AvailableSlotsQuery(RESOURCE_ID, DATE)))
                .thenThrow(new NotFoundException("Resource " + RESOURCE_ID + " not found"));

        mockMvc.perform(get("/api/resources/{id}/slots", RESOURCE_ID).param("date", "2030-01-07"))
                .andExpect(status().isNotFound());
    }
}
