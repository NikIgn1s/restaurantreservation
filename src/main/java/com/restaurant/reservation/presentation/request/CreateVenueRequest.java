package com.restaurant.reservation.presentation.request;

import com.restaurant.reservation.domain.model.VenueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVenueRequest(
        @Schema(example = "Bistro Central")
        @NotBlank @Size(max = 255) String name,
        @NotNull VenueType type,
        @Schema(example = "Main street 1")
        @Size(max = 512) String address,
        @Schema(example = "Europe/Warsaw")
        @NotBlank String timezone) {
}
