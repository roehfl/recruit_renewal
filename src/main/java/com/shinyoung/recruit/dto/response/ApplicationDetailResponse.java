package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationDetailResponse(
        Long applicationId,
        Long applicantId,
        Long jobPostingId,
        String jobPostingTitle,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ApplicationDetailResponse from(JobApplication application) {
        return new ApplicationDetailResponse(
                application.getId(),
                application.getApplicant().getId(),
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
