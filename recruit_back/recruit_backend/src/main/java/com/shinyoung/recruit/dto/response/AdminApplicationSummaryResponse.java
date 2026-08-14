package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;

import java.time.LocalDateTime;

public record AdminApplicationSummaryResponse(
        Long applicationId,
        Long applicantId,
        String applicantNameSnapshot,
        Long jobPostingId,
        String jobPostingTitleSnapshot,
        Long jobPositionId,
        String jobPositionNameSnapshot,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminApplicationSummaryResponse from(JobApplication application) {
        return new AdminApplicationSummaryResponse(
                application.getId(),
                application.getApplicant().getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosting().getId(),
                application.getJobPostingTitleSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
