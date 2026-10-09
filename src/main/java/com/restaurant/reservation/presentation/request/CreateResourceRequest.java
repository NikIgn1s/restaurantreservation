package com.restaurant.reservation.presentation.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
        @Schema(example = "Table 1")
        @NotBlank @Size(max = 255) String name,
        @Schema(example = "4")
        @Min(1) @Max(1000) int capacity) {
}
