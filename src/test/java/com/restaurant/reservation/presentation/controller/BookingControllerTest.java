package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.dto.CreateBookingCommand;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.CompleteBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.GetBookingUseCase;
import com.restaurant.reservation.application.usecase.ListMyBookingsUseCase;
import com.restaurant.reservation.application.usecase.MarkNoShowUseCase;
import com.restaurant.reservation.domain.exception.ForbiddenOperationException;
import com.restaurant.reservation.domain.exception.IdempotencyKeyConflictException;
import com.restaurant.reservation.domain.exception.InvalidBookingStateException;
import com.restaurant.reservation.domain.exception.SlotNotAvailableException;
import com.restaurant.reservation.domain.model.Booking;
import com.restaurant.reservation.domain.model.BookingStatus;
import com.restaurant.reservation.domain.model.Resource;
import com.restaurant.reservation.domain.model.TimeSlot;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActorArgumentResolver;
import com.restaurant.reservation.presentation.resolver.WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.restaurant.reservation.presentation.TestAuth.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, CurrentActorArgumentResolver.class, BookingApiMapper.class})
class BookingControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2030-01-01T09:00:00Z");
    private static final TimeSlot SLOT = new TimeSlot(Instant.parse("2030-01-07T09:00:00Z"), Instant.parse("2030-01-07T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CreateBookingUseCase createBooking;
    @MockBean
    private GetBookingUseCase getBooking;
    @MockBean
    private ListMyBookingsUseCase listMyBookings;
    @MockBean
    private CancelBookingUseCase cancelBooking;
    @MockBean
    private ConfirmBookingUseCase confirmBooking;
    @MockBean
    private CompleteBookingUseCase completeBooking;
    @MockBean
    private MarkNoShowUseCase markNoShow;

    private Resource table;
    private Booking booking;
    private String requestBody;

    @BeforeEach
    void setUp() {
        table = Resource.create(UUID.randomUUID(), "Table 1", 4);
        booking = Booking.create(table, USER_ID, SLOT, 2, NOW);
        requestBody = """
                {"resourceId": "%s", "startAt": "2030-01-07T09:00:00Z", "endAt": "2030-01-07T10:00:00Z", "guests": 2}
                """.formatted(table.getId());
    }

    private MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder request) {
        return request.with(asUser(USER_ID.toString(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void createsBookingWithStatusCreated() throws Exception {
        when(createBooking.execute(any())).thenReturn(new BookingResult(booking, false));

        mockMvc.perform(asCustomer(post("/api/bookings")).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.id").value(booking.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.startAt").value("2030-01-07T09:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2030-01-07T10:00:00Z"))
                .andExpect(jsonPath("$.guests").value(2))
                .andExpect(jsonPath("$.depositRequired").value(false));
    }

    @Test
    void passesIdempotencyKeyAndRequestToUseCase() throws Exception {
        when(createBooking.execute(any())).thenReturn(new BookingResult(booking, false));

        mockMvc.perform(asCustomer(post("/api/bookings")).header("Idempotency-Key", "key-42").content(requestBody));

        ArgumentCaptor<CreateBookingCommand> captor = ArgumentCaptor.forClass(CreateBookingCommand.class);
        verify(createBooking).execute(captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("key-42");
        assertThat(captor.getValue().resourceId()).isEqualTo(table.getId());
        assertThat(captor.getValue().slot()).isEqualTo(SLOT);
        assertThat(captor.getValue().guests()).isEqualTo(2);
        assertThat(captor.getValue().actor().id()).isEqualTo(USER_ID);
    }

    @Test
    void worksWithoutIdempotencyKey() throws Exception {
        when(createBooking.execute(any())).thenReturn(new BookingResult(booking, false));

        mockMvc.perform(asCustomer(post("/api/bookings")).content(requestBody)).andExpect(status().isCreated());

        ArgumentCaptor<CreateBookingCommand> captor = ArgumentCaptor.forClass(CreateBookingCommand.class);
        verify(createBooking).execute(captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isNull();
    }

    @Test
    void replayedRequestReturnsOkWithMarkerHeader() throws Exception {
        when(createBooking.execute(any())).thenReturn(new BookingResult(booking, true));

        mockMvc.perform(asCustomer(post("/api/bookings")).header("Idempotency-Key", "key-42").content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(booking.getId().toString()));
    }

    @Test
    void reusedKeyWithDifferentRequestIsUnprocessable() throws Exception {
        when(createBooking.execute(any())).thenThrow(new IdempotencyKeyConflictException("key-42"));

        mockMvc.perform(asCustomer(post("/api/bookings")).header("Idempotency-Key", "key-42").content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    @Test
    void takenSlotIsConflict() throws Exception {
        when(createBooking.execute(any())).thenThrow(new SlotNotAvailableException("Requested slot is not available"));

        mockMvc.perform(asCustomer(post("/api/bookings")).content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Slot not available"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized());

        verify(createBooking, never()).execute(any());
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(asCustomer(post("/api/bookings")).content("{\"guests\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.resourceId").exists())
                .andExpect(jsonPath("$.errors.startAt").exists())
                .andExpect(jsonPath("$.errors.endAt").exists())
                .andExpect(jsonPath("$.errors.guests").exists());
    }

    @Test
    void rejectsSlotWhereEndIsNotAfterStart() throws Exception {
        String body = """
                {"resourceId": "%s", "startAt": "2030-01-07T10:00:00Z", "endAt": "2030-01-07T09:00:00Z", "guests": 2}
                """.formatted(table.getId());

        mockMvc.perform(asCustomer(post("/api/bookings")).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedInstant() throws Exception {
        String body = """
                {"resourceId": "%s", "startAt": "tomorrow", "endAt": "2030-01-07T10:00:00Z", "guests": 2}
                """.formatted(table.getId());

        mockMvc.perform(asCustomer(post("/api/bookings")).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void listsMyBookings() throws Exception {
        when(listMyBookings.execute(any())).thenReturn(List.of(booking));

        mockMvc.perform(asCustomer(get("/api/bookings/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()));
    }

    @Test
    void myBookingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsBookingById() throws Exception {
        when(getBooking.execute(any())).thenReturn(booking);

        mockMvc.perform(asCustomer(get("/api/bookings/{id}", booking.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(booking.getId().toString()));

        ArgumentCaptor<BookingActionCommand> captor = ArgumentCaptor.forClass(BookingActionCommand.class);
        verify(getBooking).execute(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo(booking.getId());
        assertThat(captor.getValue().actor().id()).isEqualTo(USER_ID);
    }

    @Test
    void foreignBookingIsForbidden() throws Exception {
        when(getBooking.execute(any())).thenThrow(new ForbiddenOperationException("Booking belongs to another user"));

        mockMvc.perform(asCustomer(get("/api/bookings/{id}", booking.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedBookingId() throws Exception {
        mockMvc.perform(asCustomer(get("/api/bookings/{id}", "abc"))).andExpect(status().isBadRequest());
    }

    @Test
    void cancelsBooking() throws Exception {
        booking.cancel();
        when(cancelBooking.execute(any())).thenReturn(booking);

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/cancel", booking.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.CANCELLED.name()));
    }

    @Test
    void cancellingFinishedBookingIsConflict() throws Exception {
        when(cancelBooking.execute(any()))
                .thenThrow(new InvalidBookingStateException(BookingStatus.CANCELLED, BookingStatus.CANCELLED));

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/cancel", booking.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmsBooking() throws Exception {
        booking.confirm();
        when(confirmBooking.execute(any())).thenReturn(booking);

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/confirm", booking.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.CONFIRMED.name()));
    }

    @Test
    void recordsThatTheGuestCame() throws Exception {
        booking.confirm();
        booking.complete();
        when(completeBooking.execute(any())).thenReturn(booking);

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/complete", booking.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.COMPLETED.name()));
    }

    @Test
    void recordsThatTheGuestDidNotCome() throws Exception {
        booking.confirm();
        booking.markNoShow();
        when(markNoShow.execute(any())).thenReturn(booking);

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/no-show", booking.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BookingStatus.NO_SHOW.name()));
    }

    @Test
    void customerCannotRecordTheOutcome() throws Exception {
        when(completeBooking.execute(any())).thenThrow(new ForbiddenOperationException("no"));
        when(markNoShow.execute(any())).thenThrow(new ForbiddenOperationException("no"));

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/complete", booking.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(asCustomer(post("/api/bookings/{id}/no-show", booking.getId()))).andExpect(status().isForbidden());
    }

    @Test
    void outcomeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/bookings/{id}/complete", booking.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings/{id}/no-show", booking.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void confirmingWithoutRightsIsForbidden() throws Exception {
        when(confirmBooking.execute(any())).thenThrow(new ForbiddenOperationException("no"));

        mockMvc.perform(asCustomer(post("/api/bookings/{id}/confirm", booking.getId())))
                .andExpect(status().isForbidden());
    }
}
