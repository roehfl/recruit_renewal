package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.CareerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CareerReplaceRequest(
        @NotNull(message = "Career type is required.")
        CareerType careerType,

        @NotNull(message = "Career list is required.")
        List<@Valid CareerRequest> careers
) {
}
