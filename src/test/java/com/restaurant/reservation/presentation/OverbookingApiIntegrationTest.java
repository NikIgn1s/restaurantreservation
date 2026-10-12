package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OverbookingApiIntegrationTest extends PostgresIntegrationTest {

    private static final String DATE = "2030-01-07";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private VenueRepository venues;
    @Autowired
    private ResourceRepository resources;
    @Autowired
    private ScheduleRepository schedules;
    @MockBean
    private NoShowPredictor predictor;

    private UUID firstGuest;
    private UUID secondGuest;
    private Resource table;

    @BeforeEach
    void prepare() {
        UUID ownerId = insertUser();
        firstGuest = insertUser();
        secondGuest = insertUser();
        Venue venue = venues.save(Venue.create(ownerId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        schedules.save(Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0), 60));
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.8, "v1"), NoShowPrediction.of(0.2, "v1"));
    }

    private ResultActions book(UUID guest) throws Exception {
        String body = """
                {"resourceId": "%s", "startAt": "%sT11:00:00Z", "endAt": "%sT12:00:00Z", "guests": 2}
                """.formatted(table.getId(), DATE, DATE);
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", bearer(guest, UserRole.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions overbookable(UUID guest) throws Exception {
        return mockMvc.perform(get("/api/resources/{id}/slots/overbookable", table.getId())
                .param("date", DATE)
                .header("Authorization", bearer(guest, UserRole.CUSTOMER)));
    }

    @Test
    void overbookableSlotsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/slots/overbookable", table.getId()).param("date", DATE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nothingIsOverbookableWhileTheTableIsFree() throws Exception {
        overbookable(secondGuest).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void riskySlotIsListedAndThenBookedAsStandby() throws Exception {
        book(firstGuest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overbooked").value(false))
                .andExpect(jsonPath("$.depositRequired").value(true));

        overbookable(secondGuest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value(DATE + "T11:00:00Z"));
        book(secondGuest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.overbooked").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));
        overbookable(secondGuest).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void thirdGuestIsRejectedWithConflict() throws Exception {
        book(firstGuest).andExpect(status().isCreated());
        book(secondGuest).andExpect(status().isCreated());

        book(insertUser())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slot not available"));
    }

    @Test
    void standbyBecomesAnOrdinaryBookingWhenTheFirstGuestCancels() throws Exception {
        String primaryId = idOf(book(firstGuest).andExpect(status().isCreated()));
        String standbyId = idOf(book(secondGuest).andExpect(status().isCreated()));

        mockMvc.perform(post("/api/bookings/{id}/cancel", primaryId)
                        .header("Authorization", bearer(firstGuest, UserRole.CUSTOMER)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}", standbyId).header("Authorization", bearer(secondGuest, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overbooked").value(false))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void standbyIsCancelledWhenTheFirstGuestPaysTheDeposit() throws Exception {
        String primaryId = idOf(book(firstGuest).andExpect(status().isCreated()));
        String standbyId = idOf(book(secondGuest).andExpect(status().isCreated()));

        mockMvc.perform(post("/api/bookings/{id}/deposit", primaryId)
                        .header("Authorization", bearer(firstGuest, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/bookings/{id}", standbyId).header("Authorization", bearer(secondGuest, UserRole.CUSTOMER)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void standbyEndpointIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/resources/{resourceId}/slots/overbookable'].get").exists())
                .andExpect(jsonPath("$.components.schemas.BookingResponse.properties.overbooked").exists());
    }
}
