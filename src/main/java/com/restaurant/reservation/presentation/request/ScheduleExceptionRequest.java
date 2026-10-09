package com.restaurant.reservation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ScheduleExceptionRequest(
        @Schema(description = "true for a day off, false for special opening hours", example = "false")
        @NotNull Boolean closed,
        @Schema(type = "string", example = "12:00")
        LocalTime openTime,
        @Schema(type = "string", example = "16:00")
        LocalTime closeTime) {
}
