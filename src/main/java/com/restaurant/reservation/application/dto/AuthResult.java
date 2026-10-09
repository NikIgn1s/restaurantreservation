package com.restaurant.reservation.application.dto;

import com.restaurant.reservation.application.port.IssuedToken;
import com.restaurant.reservation.domain.model.User;

public record AuthResult(User user, IssuedToken token) {
}
