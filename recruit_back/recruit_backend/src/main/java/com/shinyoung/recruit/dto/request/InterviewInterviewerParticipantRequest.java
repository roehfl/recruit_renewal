package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InterviewInterviewerParticipantRequest(
        @NotNull(message = "Employee id is required.")
        Long employeeId,

        @PositiveOrZero(message = "Interviewer sort order must be zero or greater.")
        Integer sortOrder
) {
}
