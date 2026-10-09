package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.domain.model.UserRole;

import java.util.UUID;

public record Actor(UUID id, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean canManageVenues() {
        return role == UserRole.VENUE_OWNER || role == UserRole.ADMIN;
    }
}
