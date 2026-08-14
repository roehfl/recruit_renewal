package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AwardReplaceRequest(
        @NotNull(message = "Award list is required.")
        List<@Valid AwardRequest> awards
) {
}
