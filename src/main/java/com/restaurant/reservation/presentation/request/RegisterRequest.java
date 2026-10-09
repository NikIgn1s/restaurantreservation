package com.restaurant.reservation.presentation.request;

import com.restaurant.reservation.domain.model.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "anna@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "8 to 72 characters", example = "password123")
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "Anna Kowalska")
        @NotBlank @Size(max = 255) String fullName,
        @Schema(description = "CUSTOMER books slots, VENUE_OWNER manages venues. ADMIN cannot be self-registered",
                allowableValues = {"CUSTOMER", "VENUE_OWNER"}, example = "CUSTOMER")
        @NotNull UserRole role) {
}
