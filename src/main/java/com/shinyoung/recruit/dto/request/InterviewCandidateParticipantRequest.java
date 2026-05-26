package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InterviewCandidateParticipantRequest(
        @NotNull(message = "JobApplication id is required.")
        Long jobApplicationId,

        @PositiveOrZero(message = "Candidate sort order must be zero or greater.")
        Integer sortOrder
) {
}
