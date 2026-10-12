package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.ScheduleRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.domain.model.Schedule;
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
import java.time.Duration;
import java.time.Instant;
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
class BookingRiskApiIntegrationTest extends PostgresIntegrationTest {

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
    @Autowired
    private BookingRepository bookings;
    @MockBean
    private NoShowPredictor predictor;

    private UUID ownerId;
    private UUID guestId;
    private Resource table;
    private Instant slotStart;

    @BeforeEach
    void prepare() {
        ownerId = insertUser();
        guestId = insertUser();
        Venue venue = venues.save(Venue.create(ownerId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        Instant nextMonday = Instant.parse("2030-01-07T00:00:00Z");
        schedules.save(Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(16, 0), 60));
        slotStart = nextMonday.plus(Duration.ofHours(11));
    }

    private ResultActions bookSlot() throws Exception {
        String body = """
                {"resourceId": "%s", "startAt": "%s", "endAt": "%s", "guests": 2}
                """.formatted(table.getId(), slotStart, slotStart.plus(Duration.ofHours(1)));
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", bearer(guestId, UserRole.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private Booking pastVisit() {
        Instant start = Instant.now().minus(Duration.ofHours(2));
        Booking booking = Booking.create(table, guestId, new TimeSlot(start, start.plus(Duration.ofHours(1))), 2,
                Instant.parse("2019-01-01T00:00:00Z"));
        booking.confirm();
        return bookings.save(booking);
    }

    @Test
    void responseShowsProbabilityAndDepositForARiskyGuest() throws Exception {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.82, "v1"));

        bookSlot()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noShowProbability").value(0.82))
                .andExpect(jsonPath("$.depositRequired").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void responseHasNoDepositForASafeGuest() throws Exception {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.07, "v1"));

        bookSlot()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noShowProbability").value(0.07))
                .andExpect(jsonPath("$.depositRequired").value(false));
    }

    @Test
    void bookingStillWorksWhenThePredictionServiceIsDown() throws Exception {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.unavailable());

        bookSlot()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noShowProbability").doesNotExist())
                .andExpect(jsonPath("$.depositRequired").value(false));
    }

    @Test
    void riskyBookingGoesThroughDepositPaymentToConfirmation() throws Exception {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.9, "v1"));
        String bookingId = idOf(bookSlot().andExpect(status().isCreated()));

        mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                        .header("Authorization", bearer(guestId, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/deposit", bookingId)
                        .header("Authorization", bearer(guestId, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("PAID"));
    }

    @Test
    void venueOwnerRecordsNoShowThroughTheApi() throws Exception {
        Booking visit = pastVisit();

        mockMvc.perform(post("/api/bookings/{id}/no-show", visit.getId())
                        .header("Authorization", bearer(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void venueOwnerRecordsAttendanceThroughTheApi() throws Exception {
        Booking visit = pastVisit();

        mockMvc.perform(post("/api/bookings/{id}/complete", visit.getId())
                        .header("Authorization", bearer(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void guestCannotRecordOutcomeThroughTheApi() throws Exception {
        Booking visit = pastVisit();

        mockMvc.perform(post("/api/bookings/{id}/no-show", visit.getId())
                        .header("Authorization", bearer(guestId, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bookings/{id}/complete", visit.getId())
                        .header("Authorization", bearer(guestId, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void futureVisitOutcomeIsUnprocessable() throws Exception {
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.05, "v1"));
        String bookingId = idOf(bookSlot().andExpect(status().isCreated()));
        mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                .header("Authorization", bearer(guestId, UserRole.CUSTOMER))).andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/{id}/no-show", bookingId)
                        .header("Authorization", bearer(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void outcomeEndpointsAppearInTheApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/complete'].post").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/no-show'].post").exists());
    }
}
