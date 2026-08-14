package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record StageReorderRequest(
        @NotEmpty List<@Valid StageOrderRequest> items
) {
}
