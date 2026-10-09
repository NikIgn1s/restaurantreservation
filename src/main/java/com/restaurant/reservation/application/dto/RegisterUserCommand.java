package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.UserRole;

public record RegisterUserCommand(String email, String password, String fullName, UserRole role) {
}
