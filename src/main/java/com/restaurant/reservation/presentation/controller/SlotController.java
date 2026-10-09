package com.restaurant.reservation.presentation.controller;

import com.restaurant.reservation.application.dto.AvailableSlotsQuery;
import com.restaurant.reservation.application.usecase.GetAvailableSlotsUseCase;
import com.restaurant.reservation.presentation.mapper.BookingApiMapper;
import com.restaurant.reservation.presentation.response.SlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
@RequestMapping("/api/resources/{resourceId}/slots")
@RequiredArgsConstructor
@Tag(name = "Slots", description = "Free time slots of a resource")
public class SlotController {

    private final GetAvailableSlotsUseCase availableSlots;
    private final BookingApiMapper mapper;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Free slots for a date",
            description = "The date is interpreted in the time zone of the venue. Booked and past slots are not returned")
    public List<SlotResponse> slots(@PathVariable UUID resourceId,
                                    @Parameter(description = "yyyy-MM-dd", example = "2030-01-07")
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availableSlots.execute(new AvailableSlotsQuery(resourceId, date)).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
