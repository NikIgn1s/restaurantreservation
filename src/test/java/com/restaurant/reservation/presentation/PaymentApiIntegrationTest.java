package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.domain.model.Venue;
import com.restaurant.reservation.domain.model.VenueType;
import com.restaurant.reservation.domain.repository.BookingRepository;
import com.restaurant.reservation.domain.repository.ResourceRepository;
import com.restaurant.reservation.domain.repository.VenueRepository;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PaymentApiIntegrationTest extends PostgresIntegrationTest {

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
    private BookingRepository bookings;

    private UUID ownerId;
    private Resource table;
    private int nextHour;

    @BeforeEach
    void prepare() {
        ownerId = insertUser();
        Venue venue = venues.save(Venue.create(ownerId, "Bistro", VenueType.RESTAURANT, "Main st 1", ZoneId.of("UTC")));
        table = resources.save(Resource.create(venue.getId(), "Table 1", 4));
        nextHour = 10;
    }

    private UUID userWithEmail(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, 'hash', 'Guest', 'CUSTOMER')",
                id, email);
        return id;
    }

    private Booking booking(UUID userId, boolean depositRequired) {
        Instant start = Instant.parse("2030-01-07T00:00:00Z").plus(Duration.ofHours(nextHour++));
        Booking booking = Booking.create(table, userId, new TimeSlot(start, start.plus(Duration.ofHours(1))), 2, CREATED);
        if (depositRequired) {
            booking.assessNoShowRisk(0.9, 0.4);
        }
        return bookings.save(booking);
    }

    private String token(UUID userId) {
        return bearer(userId, UserRole.CUSTOMER);
    }

    @Test
    void payingTheDepositConfirmsTheBooking() throws Exception {
        UUID guest = insertUser();
        Booking booking = booking(guest, true);

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.amount").value(20.0))
                .andExpect(jsonPath("$.payment.currency").value("PLN"))
                .andExpect(jsonPath("$.payment.reference").value(org.hamcrest.Matchers.startsWith("fake-")))
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/bookings/{id}", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void declinedCardGivesPaymentRequiredAndKeepsBookingPending() throws Exception {
        UUID guest = userWithEmail("decline.me@test.com");
        Booking booking = booking(guest, true);

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.booking.status").value("PENDING"));
    }

    @Test
    void payerCanRetryAfterADecline() throws Exception {
        UUID guest = userWithEmail("decline.me@test.com");
        Booking booking = booking(guest, true);

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isPaymentRequired());
        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isPaymentRequired());

        String body = mockMvc.perform(get("/api/bookings/{id}/payments", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode payments = objectMapper.readTree(body);
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(payments.get(1).get("status").asText()).isEqualTo("FAILED");
    }

    @Test
    void secondPaymentAfterSuccessIsConflict() throws Exception {
        UUID guest = insertUser();
        Booking booking = booking(guest, true);
        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid booking state"));
    }

    @Test
    void anotherUserCannotPay() throws Exception {
        UUID guest = insertUser();
        Booking booking = booking(guest, true);

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(insertUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingWithoutDepositIsUnprocessable() throws Exception {
        UUID guest = insertUser();
        Booking booking = booking(guest, false);

        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownBookingIsNotFound() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/deposit", UUID.randomUUID()).header("Authorization", token(insertUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void depositRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/deposit", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/{id}/payments", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void venueOwnerSeesPaymentsButStrangerDoesNot() throws Exception {
        UUID guest = insertUser();
        Booking booking = booking(guest, true);
        mockMvc.perform(post("/api/bookings/{id}/deposit", booking.getId()).header("Authorization", token(guest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/{id}/payments", booking.getId()).header("Authorization", bearer(ownerId, UserRole.VENUE_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PAID"));
        mockMvc.perform(get("/api/bookings/{id}/payments", booking.getId()).header("Authorization", token(insertUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDescribesPaymentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/deposit'].post").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/payments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/deposit'].post.responses['402']").exists());
    }
}
