package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InterviewSupplementWindowResetRequest(
        @NotEmpty List<@NotNull Long> jobApplicationIds
) {
}
