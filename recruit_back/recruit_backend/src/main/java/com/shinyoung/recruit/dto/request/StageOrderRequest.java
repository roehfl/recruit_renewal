package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StageOrderRequest(
        @NotNull Long stageId,
        @NotNull @Min(0) Integer stageOrder
) {
}
