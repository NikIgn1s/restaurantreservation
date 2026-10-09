package com.restaurant.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AddScheduleExceptionCommand(Actor actor, UUID resourceId, LocalDate date, boolean closed,
                                          LocalTime openTime, LocalTime closeTime) {
}
