package com.restaurant.reservation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "alice@dev.local")
        @NotBlank String email,
        @Schema(example = "password123")
        @NotBlank String password) {
}
