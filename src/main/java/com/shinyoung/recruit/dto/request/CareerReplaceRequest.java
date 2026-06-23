package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CareerReplaceRequest(
        @NotNull(message = "Career list is required.")
        List<@Valid CareerRequest> careers
) {
}
