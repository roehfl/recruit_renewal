package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;

import java.time.LocalDateTime;

public record AdminApplicationDetailResponse(
        Long applicationId,
        Long applicantId,
        String applicantNameSnapshot,
        Long jobPostingId,
        String jobPostingTitleSnapshot,
        Long jobPositionId,
        String jobPositionNameSnapshot,
        /** 지원자가 선택한 근무지 표시명. 근무지 후보가 없는 모집분야면 null. */
        String workLocationNameSnapshot,
        JobApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminApplicationDetailResponse from(JobApplication application) {
        return new AdminApplicationDetailResponse(
                application.getId(),
                application.getApplicant().getId(),
                application.getApplicantNameSnapshot(),
                application.getJobPosting().getId(),
                application.getJobPostingTitleSnapshot(),
                application.getJobPosition().getId(),
                application.getJobPositionNameSnapshot(),
                application.getWorkLocationNameSnapshot(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
