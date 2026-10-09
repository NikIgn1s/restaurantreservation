package com.restaurant.reservation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID resourceId,
        @Schema(description = "Slot start exactly as returned by the slots endpoint", example = "2030-01-07T09:00:00Z")
        @NotNull Instant startAt,
        @Schema(description = "Slot end exactly as returned by the slots endpoint", example = "2030-01-07T10:00:00Z")
        @NotNull Instant endAt,
        @Schema(example = "2")
        @Min(1) @Max(1000) int guests) {
}
