package com.restaurant.reservation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservation.domain.model.UserRole;
import com.restaurant.reservation.infrastructure.persistence.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookingApiIntegrationTest extends PostgresIntegrationTest {

    private static final String DATE = "2030-01-07";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID ownerId;
    private UUID aliceId;
    private UUID bobId;
    private String resourceId;

    @BeforeEach
    void prepare() throws Exception {
        ownerId = insertUser();
        aliceId = insertUser();
        bobId = insertUser();
        String venueId = idOf(send(post("/api/venues"), ownerId, "VENUE_OWNER", null,
                "{\"name\": \"Bistro\", \"type\": \"RESTAURANT\", \"timezone\": \"UTC\"}").andExpect(status().isCreated()));
        resourceId = idOf(send(post("/api/venues/{id}/resources", venueId), ownerId, "VENUE_OWNER", null,
                "{\"name\": \"Table 1\", \"capacity\": 4}").andExpect(status().isCreated()));
        send(put("/api/resources/{id}/schedule/MONDAY", resourceId), ownerId, "VENUE_OWNER", null,
                "{\"openTime\": \"10:00\", \"closeTime\": \"13:00\", \"slotMinutes\": 60}").andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID userId, String role, String idempotencyKey,
                               String body) throws Exception {
        request.header("Authorization", bearer(userId, UserRole.valueOf(role))).contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            request.content(body);
        }
        return mockMvc.perform(request);
    }

    private String idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String bookingBody(int startHour, int guests) {
        return """
                {"resourceId": "%s", "startAt": "%sT%02d:00:00Z", "endAt": "%sT%02d:00:00Z", "guests": %d}
                """.formatted(resourceId, DATE, startHour, DATE, startHour + 1, guests);
    }

    private ResultActions book(UUID userId, int startHour, String key) throws Exception {
        return send(post("/api/bookings"), userId, "CUSTOMER", key, bookingBody(startHour, 2));
    }

    private JsonNode freeSlots() throws Exception {
        String body = mockMvc.perform(get("/api/resources/{id}/slots", resourceId).param("date", DATE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void listsAllSlotsOfTheDay() throws Exception {
        JsonNode slots = freeSlots();

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).get("startAt").asText()).isEqualTo("2030-01-07T10:00:00Z");
        assertThat(slots.get(2).get("endAt").asText()).isEqualTo("2030-01-07T13:00:00Z");
    }

    @Test
    void bookedSlotDisappearsFromSlotList() throws Exception {
        book(aliceId, 11, null).andExpect(status().isCreated());

        JsonNode slots = freeSlots();

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).get("startAt").asText()).isEqualTo("2030-01-07T10:00:00Z");
        assertThat(slots.get(1).get("startAt").asText()).isEqualTo("2030-01-07T12:00:00Z");
    }

    @Test
    void createdBookingBelongsToCaller() throws Exception {
        book(aliceId, 11, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(aliceId.toString()))
                .andExpect(jsonPath("$.resourceId").value(resourceId))
                .andExpect(jsonPath("$.startAt").value("2030-01-07T11:00:00Z"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void repeatedRequestWithSameKeyReturnsOriginalBookingWithOk() throws Exception {
        String firstId = idOf(book(aliceId, 11, "key-1").andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed")));

        ResultActions replay = book(aliceId, 11, "key-1");

        replay.andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(firstId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
    }

    @Test
    void replayReturnsExactlyTheSameBody() throws Exception {
        String original = book(aliceId, 11, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = book(aliceId, 11, "key-1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay)).isEqualTo(objectMapper.readTree(original));
    }

    @Test
    void sameKeyWithDifferentSlotIsUnprocessable() throws Exception {
        book(aliceId, 11, "key-1").andExpect(status().isCreated());

        book(aliceId, 12, "key-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    @Test
    void sameKeyOfAnotherUserCreatesSeparateBooking() throws Exception {
        String aliceBooking = idOf(book(aliceId, 10, "shared").andExpect(status().isCreated()));
        String bobBooking = idOf(book(bobId, 11, "shared").andExpect(status().isCreated()));

        assertThat(aliceBooking).isNotEqualTo(bobBooking);
    }

    @Test
    void takenSlotIsConflict() throws Exception {
        book(aliceId, 11, null).andExpect(status().isCreated());

        book(bobId, 11, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slot not available"));
    }

    @Test
    void tooManyGuestsIsUnprocessable() throws Exception {
        send(post("/api/bookings"), aliceId, "CUSTOMER", null, bookingBody(11, 9))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void slotOutsideScheduleIsConflict() throws Exception {
        book(aliceId, 15, null).andExpect(status().isConflict());
    }

    @Test
    void myBookingsContainOnlyOwnOnesNewestFirst() throws Exception {
        book(aliceId, 10, null).andExpect(status().isCreated());
        book(aliceId, 12, null).andExpect(status().isCreated());
        book(bobId, 11, null).andExpect(status().isCreated());

        String body = send(get("/api/bookings/me"), aliceId, "CUSTOMER", null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode mine = objectMapper.readTree(body);

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).get("startAt").asText()).isEqualTo("2030-01-07T12:00:00Z");
        assertThat(mine.get(1).get("startAt").asText()).isEqualTo("2030-01-07T10:00:00Z");
    }

    @Test
    void ownerAndVenueOwnerSeeBookingButStrangerDoesNot() throws Exception {
        String bookingId = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));

        send(get("/api/bookings/{id}", bookingId), aliceId, "CUSTOMER", null, null).andExpect(status().isOk());
        send(get("/api/bookings/{id}", bookingId), ownerId, "VENUE_OWNER", null, null).andExpect(status().isOk());
        send(get("/api/bookings/{id}", bookingId), bobId, "CUSTOMER", null, null).andExpect(status().isForbidden());
    }

    @Test
    void unknownBookingIsNotFound() throws Exception {
        send(get("/api/bookings/{id}", UUID.randomUUID()), aliceId, "CUSTOMER", null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelledBookingFreesTheSlot() throws Exception {
        String bookingId = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));

        send(post("/api/bookings/{id}/cancel", bookingId), aliceId, "CUSTOMER", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(freeSlots()).hasSize(3);
        book(bobId, 11, null).andExpect(status().isCreated());
    }

    @Test
    void cancelTwiceIsConflict() throws Exception {
        String bookingId = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));
        send(post("/api/bookings/{id}/cancel", bookingId), aliceId, "CUSTOMER", null, null).andExpect(status().isOk());

        send(post("/api/bookings/{id}/cancel", bookingId), aliceId, "CUSTOMER", null, null)
                .andExpect(status().isConflict());
    }

    @Test
    void strangerCannotCancel() throws Exception {
        String bookingId = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));

        send(post("/api/bookings/{id}/cancel", bookingId), bobId, "CUSTOMER", null, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void venueOwnerCancelsAndConfirmsCustomerBooking() throws Exception {
        String first = idOf(book(aliceId, 10, null).andExpect(status().isCreated()));
        String second = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));

        send(post("/api/bookings/{id}/confirm", first), ownerId, "VENUE_OWNER", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(post("/api/bookings/{id}/cancel", second), ownerId, "VENUE_OWNER", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerConfirmsOwnBookingAndSlotStaysBlocked() throws Exception {
        String bookingId = idOf(book(aliceId, 11, null).andExpect(status().isCreated()));

        send(post("/api/bookings/{id}/confirm", bookingId), aliceId, "CUSTOMER", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(1));

        book(bobId, 11, null).andExpect(status().isConflict());
    }

    @Test
    void bookingEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(bookingBody(11, 2)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDescribesBookingEndpointsAndIdempotencyHeader() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings']").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/me']").exists())
                .andExpect(jsonPath("$.paths['/api/bookings/{bookingId}/cancel']").exists())
                .andExpect(jsonPath("$.paths['/api/resources/{resourceId}/slots']").exists())
                .andExpect(jsonPath("$.paths['/api/bookings'].post.parameters[?(@.name=='Idempotency-Key')]").exists());
    }
}
