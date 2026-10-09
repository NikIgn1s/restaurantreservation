package com.restaurant.reservation.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleResponse(UUID id, UUID resourceId, DayOfWeek dayOfWeek,
                               @Schema(type = "string", example = "10:00") LocalTime openTime,
                               @Schema(type = "string", example = "22:00") LocalTime closeTime,
                               int slotMinutes) {
}
