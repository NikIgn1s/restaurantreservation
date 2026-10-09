package com.restaurant.reservation.presentation.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleExceptionResponse(UUID id, UUID resourceId, LocalDate date, boolean closed,
                                        @Schema(type = "string", example = "12:00") LocalTime openTime,
                                        @Schema(type = "string", example = "16:00") LocalTime closeTime) {
}
