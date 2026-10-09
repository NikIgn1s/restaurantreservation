package com.restaurant.reservation.presentation.handler;

import com.restaurant.reservation.application.usecase.CreateResourceUseCase;
import com.restaurant.reservation.application.usecase.CreateVenueUseCase;
import com.restaurant.reservation.application.usecase.ListVenueResourcesUseCase;
import com.restaurant.reservation.application.usecase.ListVenuesUseCase;
import com.restaurant.reservation.domain.exception.EmailAlreadyUsedException;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingException;
import com.restaurant.reservation.domain.exception.InvalidCredentialsException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.InvalidTimeSlotException;
import com.restaurant.reservation.domain.exception.NotFoundException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.presentation.controller.VenueController;
import com.restaurant.reservation.presentation.mapper.VenueApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DateTimeException;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueController.class)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, VenueApiMapper.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CreateVenueUseCase createVenue;
    @MockBean
    private ListVenuesUseCase listVenues;
    @MockBean
    private CreateResourceUseCase createResource;
    @MockBean
    private ListVenueResourcesUseCase listResources;

    static Stream<Arguments> mappings() {
        return Stream.of(
                Arguments.of(new NotFoundException("missing"), 404, "Not found"),
                Arguments.of(new ForbiddenOperationException("denied"), 403, "Forbidden"),
                Arguments.of(new SlotNotAvailableException("taken"), 409, "Slot not available"),
                Arguments.of(new EmailAlreadyUsedException("a@b.c"), 409, "Email already registered"),
                Arguments.of(new InvalidCredentialsException(), 401, "Invalid credentials"),
                Arguments.of(new InvalidBookingStateException(BookingStatus.CANCELLED, BookingStatus.CONFIRMED), 409,
                        "Invalid booking state"),
                Arguments.of(new IdempotencyKeyConflictException("key"), 422, "Idempotency key conflict"),
                Arguments.of(new InvalidBookingException("too many guests"), 422, "Invalid booking"),
                Arguments.of(new InvalidTimeSlotException("bad slot"), 400, "Bad request"),
                Arguments.of(new IllegalArgumentException("bad argument"), 400, "Bad request"),
                Arguments.of(new DateTimeException("bad time"), 400, "Bad request"),
                Arguments.of(new IllegalStateException("secret internals"), 500, "Internal error"));
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void mapsExceptionToProblemDetail(RuntimeException exception, int expectedStatus, String expectedTitle) throws Exception {
        when(listVenues.execute()).thenThrow(exception);

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").value(expectedTitle));
    }

    @Test
    void doesNotLeakInternalErrorDetails() throws Exception {
        when(listVenues.execute()).thenThrow(new IllegalStateException("secret internals"));

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    void unknownPathGivesNotFoundInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethodGivesMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/venues"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void malformedJsonGivesBadRequest() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedMediaTypeGivesUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .header("X-User-Role", "VENUE_OWNER")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Bistro"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
