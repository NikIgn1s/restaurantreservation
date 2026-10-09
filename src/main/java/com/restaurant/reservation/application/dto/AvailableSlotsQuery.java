package com.restaurant.reservation.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AvailableSlotsQuery(UUID resourceId, LocalDate date) {
}
