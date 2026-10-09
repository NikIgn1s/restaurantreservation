package com.restaurant.reservation.application.port;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
