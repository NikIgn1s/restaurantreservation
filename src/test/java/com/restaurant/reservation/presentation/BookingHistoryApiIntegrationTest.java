package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.application.port.NoShowPrediction;
import com.restaurant.reservation.application.port.NoShowPredictor;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.Schedule;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
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
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookingHistoryApiIntegrationTest extends PostgresIntegrationTest {

    private static final Instant CREATED = Instant.parse("2029-12-01T00:00:00Z");

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
    private Venue venue;
    private Resource table;

    @BeforeEach
    void prepare() {
        ownerId = insertUser();
        guestId = insertUser();
        venue = venues.save(Venue.create(ownerId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("Europe/Warsaw")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        schedules.save(Schedule.create(table.getId(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(22, 0), 60));
        when(predictor.predict(any())).thenReturn(NoShowPrediction.of(0.1, "v1"));
    }

    private String as(UUID userId, UserRole role) {
        return bearer(userId, role);
    }

    private ResultActions bookSlotAt(UUID user, String start, String end) throws Exception {
        String body = """
                {"resourceId": "%s", "startAt": "%s", "endAt": "%s", "guests": 2}
                """.formatted(table.getId(), start, end);
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", as(user, UserRole.CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createBooking() throws Exception {
        String content = bookSlotAt(guestId, "2030-01-07T10:00:00Z", "2030-01-07T11:00:00Z")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("id").asText();
    }

    private ResultActions events(String bookingId, UUID user, UserRole role) throws Exception {
        return mockMvc.perform(get("/api/bookings/{id}/events", bookingId).header("Authorization", as(user, role)));
    }

    private ResultActions dayOf(UUID venueId, String date, UUID user, UserRole role) throws Exception {
        return mockMvc.perform(get("/api/venues/{id}/bookings", venueId).param("date", date)
                .header("Authorization", as(user, role)));
    }

    private Booking seed(Resource resource, UUID user, String startUtc) {
        Instant start = Instant.parse(startUtc);
        return bookings.save(Booking.create(resource, user, new TimeSlot(start, start.plusSeconds(3600)), 2, CREATED));
    }

    @Test
    void guestSeesTheCreationOfTheirBooking() throws Exception {
        String bookingId = createBooking();

        events(bookingId, guestId, UserRole.CUSTOMER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[0].payload.actor.type").value("user"))
                .andExpect(jsonPath("$[0].payload.actor.id").value(guestId.toString()))
                .andExpect(jsonPath("$[0].payload.actor.role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].payload.guests").value(2))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void timelineNamesWhoCancelledTheBooking() throws Exception {
        String bookingId = createBooking();
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId).header("Authorization", as(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isOk());

        String body = events(bookingId, guestId, UserRole.CUSTOMER).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode timeline = objectMapper.readTree(body);

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).get("type").asText()).isEqualTo("CREATED");
        assertThat(timeline.get(1).get("type").asText()).isEqualTo("CANCELLED");
        assertThat(timeline.get(1).get("payload").get("actor").get("id").asText()).isEqualTo(ownerId.toString());
        assertThat(timeline.get(1).get("payload").get("actor").get("role").asText()).isEqualTo("VENUE_OWNER");
        assertThat(timeline.get(1).get("payload").get("from").asText()).isEqualTo("PENDING");
    }

    @Test
    void venueOwnerAndAdminCanReadTheTimeline() throws Exception {
        String bookingId = createBooking();

        events(bookingId, ownerId, UserRole.VENUE_OWNER).andExpect(status().isOk());
        events(bookingId, insertUser(), UserRole.ADMIN).andExpect(status().isOk());
    }

    @Test
    void strangerCannotReadTheTimeline() throws Exception {
        String bookingId = createBooking();

        events(bookingId, insertUser(), UserRole.CUSTOMER).andExpect(status().isForbidden());
        events(bookingId, insertUser(), UserRole.VENUE_OWNER).andExpect(status().isForbidden());
    }

    @Test
    void timelineRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/bookings/{id}/events", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownBookingHasNoTimeline() throws Exception {
        events(UUID.randomUUID().toString(), guestId, UserRole.CUSTOMER).andExpect(status().isNotFound());
    }

    @Test
    void malformedBookingIdIsBadRequest() throws Exception {
        events("abc", guestId, UserRole.CUSTOMER).andExpect(status().isBadRequest());
    }

    @Test
    void ownerSeesEveryBookingOfTheDayInStartOrderIncludingCancelled() throws Exception {
        seed(table, guestId, "2030-01-07T14:00:00Z");
        Booking cancelled = seed(table, insertUser(), "2030-01-07T09:00:00Z");
        cancelled.cancel();
        bookings.save(cancelled);
        seed(table, insertUser(), "2030-01-07T11:00:00Z");

        String body = dayOf(venue.getId(), "2030-01-07", ownerId, UserRole.VENUE_OWNER)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode day = objectMapper.readTree(body);

        assertThat(day).hasSize(3);
        assertThat(day.get(0).get("startAt").asText()).isEqualTo("2030-01-07T09:00:00Z");
        assertThat(day.get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(day.get(1).get("startAt").asText()).isEqualTo("2030-01-07T11:00:00Z");
        assertThat(day.get(2).get("startAt").asText()).isEqualTo("2030-01-07T14:00:00Z");
    }

    @Test
    void dayBoundariesFollowTheVenueTimezone() throws Exception {
        seed(table, guestId, "2030-01-06T22:30:00Z");
        seed(table, insertUser(), "2030-01-06T23:30:00Z");
        seed(table, insertUser(), "2030-01-07T22:30:00Z");
        seed(table, insertUser(), "2030-01-07T23:30:00Z");

        dayOf(venue.getId(), "2030-01-07", ownerId, UserRole.VENUE_OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-06T23:30:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2030-01-07T22:30:00Z"));
        dayOf(venue.getId(), "2030-01-06", ownerId, UserRole.VENUE_OWNER)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-06T22:30:00Z"));
        dayOf(venue.getId(), "2030-01-08", ownerId, UserRole.VENUE_OWNER)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startAt").value("2030-01-07T23:30:00Z"));
    }

    @Test
    void emptyDayGivesAnEmptyList() throws Exception {
        dayOf(venue.getId(), "2030-01-07", ownerId, UserRole.VENUE_OWNER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bookingsOfOtherVenuesAreNotMixedIn() throws Exception {
        Venue another = venues.save(Venue.create(ownerId, "Gym", VenueType.SPORT, "Park st 2", ZoneId.of("Europe/Warsaw")));
        Resource court = resources.save(Resource.create(another.getId(), "Court 1", 10));
        seed(table, guestId, "2030-01-07T11:00:00Z");
        seed(court, guestId, "2030-01-07T11:00:00Z");

        dayOf(venue.getId(), "2030-01-07", ownerId, UserRole.VENUE_OWNER).andExpect(jsonPath("$.length()").value(1));
        dayOf(another.getId(), "2030-01-07", ownerId, UserRole.VENUE_OWNER)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resourceId").value(court.getId().toString()));
    }

    @Test
    void adminCanSeeAnyVenueDay() throws Exception {
        seed(table, guestId, "2030-01-07T11:00:00Z");

        dayOf(venue.getId(), "2030-01-07", insertUser(), UserRole.ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void customersAndForeignOwnersCannotListVenueBookings() throws Exception {
        dayOf(venue.getId(), "2030-01-07", guestId, UserRole.CUSTOMER).andExpect(status().isForbidden());
        dayOf(venue.getId(), "2030-01-07", insertUser(), UserRole.VENUE_OWNER).andExpect(status().isForbidden());
    }

    @Test
    void unknownVenueIsNotFound() throws Exception {
        dayOf(UUID.randomUUID(), "2030-01-07", ownerId, UserRole.VENUE_OWNER).andExpect(status().isNotFound());
    }

    @Test
    void dateIsRequiredAndMustBeValid() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/bookings", venue.getId())
                        .header("Authorization", as(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isBadRequest());
        dayOf(venue.getId(), "07.01.2030", ownerId, UserRole.VENUE_OWNER).andExpect(status().isBadRequest());
    }

    @Test
    void venueBookingsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/bookings", venue.getId()).param("date", "2030-01-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newEndpointsAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/events'].get").exists())
                .andExpect(jsonPath("$.paths['/api/venues/{venueId}/bookings'].get").exists());
    }
}
