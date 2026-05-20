package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.time.LocalDateTime;

public record MyApplicationResponse(
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        JobPostingStatus jobPostingStatus,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        boolean accepting,
        long announcedResultCount,
        String latestAnnouncedStageName,
        StageResultStatus latestResultStatus
) {

    public static MyApplicationResponse from(
            JobApplication application,
            boolean accepting,
            long announcedResultCount,
            String latestAnnouncedStageName,
            StageResultStatus latestResultStatus
    ) {
        JobPosting jobPosting = application.getJobPosting();
        JobPosition jobPosition = application.getJobPosition();
        return new MyApplicationResponse(
                application.getId(),
                jobPosting.getId(),
                resolveText(application.getJobPostingTitleSnapshot(), jobPosting.getTitle()),
                jobPosting.getStatus(),
                jobPosition.getId(),
                resolveText(application.getJobPositionNameSnapshot(), jobPosition.getPositionName()),
                application.getStatus(),
                application.getCreatedAt(),
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                accepting,
                announcedResultCount,
                latestAnnouncedStageName,
                latestResultStatus
        );
    }

    private static String resolveText(String snapshot, String fallback) {
        if (snapshot != null && !snapshot.isBlank()) {
            return snapshot;
        }
        return fallback;
    }
}
