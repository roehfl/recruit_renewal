package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record InterviewParticipantReplaceRequest(
        @NotNull(message = "Candidate participant list is required.")
        List<@Valid InterviewCandidateParticipantRequest> candidates,

        @NotNull(message = "Interviewer participant list is required.")
        List<@Valid InterviewInterviewerParticipantRequest> interviewers
) {
}
