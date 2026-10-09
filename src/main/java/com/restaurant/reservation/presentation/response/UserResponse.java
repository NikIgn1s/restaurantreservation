package com.restaurant.reservation.presentation.response;

import com.restaurant.reservation.domain.model.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, UserRole role, Instant createdAt) {
}
