package com.restaurant.reservation.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record SetWeeklyScheduleCommand(Actor actor, UUID resourceId, DayOfWeek dayOfWeek,
                                       LocalTime openTime, LocalTime closeTime, int slotMinutes) {
}
