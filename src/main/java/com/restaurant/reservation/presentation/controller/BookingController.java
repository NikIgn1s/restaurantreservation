package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.BookingActionCommand;
import com.restaurant.reservation.application.dto.BookingResult;
import com.restaurant.reservation.application.usecase.CancelBookingUseCase;
import com.restaurant.reservation.application.usecase.CompleteBookingUseCase;
import com.restaurant.reservation.application.usecase.ConfirmBookingUseCase;
import com.restaurant.reservation.application.usecase.CreateBookingUseCase;
import com.restaurant.reservation.application.usecase.GetBookingEventsUseCase;
import com.restaurant.reservation.application.usecase.GetBookingUseCase;
import com.restaurant.reservation.application.usecase.ListMyBookingsUseCase;
import com.restaurant.reservation.application.usecase.MarkNoShowUseCase;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.mapper.BookingEventApiMapper;
import com.restaurant.reservation.presentation.request.CreateBookingRequest;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.BookingEventResponse;
import com.restaurant.reservation.presentation.response.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create, view, confirm and cancel bookings")
public class BookingController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private final CreateBookingUseCase createBooking;
    private final GetBookingUseCase getBooking;
    private final ListMyBookingsUseCase listMyBookings;
    private final CancelBookingUseCase cancelBooking;
    private final ConfirmBookingUseCase confirmBooking;
    private final CompleteBookingUseCase completeBooking;
    private final MarkNoShowUseCase markNoShow;
    private final GetBookingEventsUseCase getBookingEvents;
    private final BookingApiMapper mapper;
    private final BookingEventApiMapper eventMapper;

    @PostMapping
    @Operation(summary = "Book a slot",
            description = "The slot must be one of the free slots of the resource. "
                    + "Send the same Idempotency-Key to safely repeat the request: the original booking is returned with status 200")
    @ApiResponse(responseCode = "201", description = "Booking created")
    @ApiResponse(responseCode = "200", description = "Request with this Idempotency-Key was already processed")
    @ApiResponse(responseCode = "409", description = "Slot is not available")
    @ApiResponse(responseCode = "422", description = "Idempotency-Key was used with a different request")
    public ResponseEntity<BookingResponse> create(
            @CurrentActor Actor actor,
            @Parameter(description = "Unique key of the client request, up to 128 characters", example = "7d5c3a9e-booking-1")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResult result = createBooking.execute(mapper.toCommand(actor, request, idempotencyKey));
        BookingResponse body = mapper.toResponse(result.booking());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    @Operation(summary = "My bookings, newest first")
    public List<BookingResponse> mine(@CurrentActor Actor actor) {
        return listMyBookings.execute(actor).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Booking details", description = "Visible to the booking owner, the venue owner and ADMIN")
    public BookingResponse get(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return mapper.toResponse(getBooking.execute(new BookingActionCommand(actor, bookingId)));
    }

    @GetMapping("/{bookingId}/events")
    @Operation(summary = "History of a booking",
            description = "Chronological log of what happened to the booking and who did it. "
                    + "Visible to the booking owner, the venue owner and ADMIN")
    public List<BookingEventResponse> events(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return getBookingEvents.execute(new BookingActionCommand(actor, bookingId)).stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    @PostMapping("/{bookingId}/confirm")
    @Operation(summary = "Confirm a booking",
            description = "The venue owner and ADMIN can always confirm. The customer can confirm only when no deposit is required")
    public BookingResponse confirm(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return mapper.toResponse(confirmBooking.execute(new BookingActionCommand(actor, bookingId)));
    }

    @PostMapping("/{bookingId}/complete")
    @Operation(summary = "Record that the guest came",
            description = "Allowed for the venue owner and ADMIN once the visit has started. Feeds the guest history used for risk prediction")
    public BookingResponse complete(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return mapper.toResponse(completeBooking.execute(new BookingActionCommand(actor, bookingId)));
    }

    @PostMapping("/{bookingId}/no-show")
    @Operation(summary = "Record that the guest did not come",
            description = "Allowed for the venue owner and ADMIN once the visit has started. Feeds the guest history used for risk prediction")
    public BookingResponse noShow(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return mapper.toResponse(markNoShow.execute(new BookingActionCommand(actor, bookingId)));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel a booking", description = "Allowed for the booking owner, the venue owner and ADMIN")
    public BookingResponse cancel(@CurrentActor Actor actor, @PathVariable UUID bookingId) {
        return mapper.toResponse(cancelBooking.execute(new BookingActionCommand(actor, bookingId)));
    }
}
