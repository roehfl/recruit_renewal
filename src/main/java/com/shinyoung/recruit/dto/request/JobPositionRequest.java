package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobPositionRequest(
        @NotBlank String positionName,
        @NotNull @Min(1) Integer headcount,
        @NotNull @Min(0) Integer sortOrder
) {
}
