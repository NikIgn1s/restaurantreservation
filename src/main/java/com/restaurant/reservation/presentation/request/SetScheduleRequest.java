package com.restaurant.reservation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SetScheduleRequest(
        @Schema(type = "string", example = "10:00")
        @NotNull LocalTime openTime,
        @Schema(type = "string", example = "22:00")
        @NotNull LocalTime closeTime,
        @Schema(example = "60")
        @NotNull @Min(5) @Max(1440) Integer slotMinutes) {
}
