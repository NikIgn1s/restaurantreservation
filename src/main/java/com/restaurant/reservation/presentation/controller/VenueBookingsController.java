package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.Actor;
import com.restaurant.reservation.application.dto.VenueBookingsQuery;
import com.restaurant.reservation.application.usecase.ListVenueBookingsUseCase;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.resolver.CurrentActor;
import com.restaurant.reservation.presentation.response.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues/{venueId}/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create, view, confirm and cancel bookings")
public class VenueBookingsController {

    private final ListVenueBookingsUseCase listVenueBookings;
    private final BookingApiMapper mapper;

    @GetMapping
    @Operation(summary = "All bookings of a venue for a day",
            description = "Every status is included, ordered by start time. The day is taken in the time zone of the venue. "
                    + "Allowed for the venue owner and ADMIN")
    public List<BookingResponse> bookings(@CurrentActor Actor actor, @PathVariable UUID venueId,
                                          @Parameter(description = "yyyy-MM-dd", example = "2030-01-07")
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return listVenueBookings.execute(new VenueBookingsQuery(actor, venueId, date)).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
