package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EducationReplaceRequest(
        @NotNull(message = "Education list is required.")
        List<@Valid EducationRequest> educations
) {
}
