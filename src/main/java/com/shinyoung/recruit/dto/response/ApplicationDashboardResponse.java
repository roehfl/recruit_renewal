package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationDashboardResponse(
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        boolean accepting,
        boolean editable,
        boolean submittable,
        boolean withdrawable,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        ApplicationCompletionSummaryResponse completionSummary,
        List<ApplicationSectionReadinessResponse> requiredMissingSections,
        List<ApplicationSectionReadinessResponse> optionalIncompleteSections,
        String latestAnnouncedStageName,
        StageResultStatus latestResultStatus
) {

    public static ApplicationDashboardResponse from(
            JobApplication application,
            boolean accepting,
            boolean editable,
            boolean submittable,
            boolean withdrawable,
            ApplicationCompletionSummaryResponse completionSummary,
            List<ApplicationSectionReadinessResponse> requiredMissingSections,
            List<ApplicationSectionReadinessResponse> optionalIncompleteSections,
            String latestAnnouncedStageName,
            StageResultStatus latestResultStatus
    ) {
        JobPosting jobPosting = application.getJobPosting();
        JobPosition jobPosition = application.getJobPosition();
        return new ApplicationDashboardResponse(
                application.getId(),
                jobPosting.getId(),
                resolveText(application.getJobPostingTitleSnapshot(), jobPosting.getTitle()),
                resolveText(application.getJobPositionNameSnapshot(), jobPosition.getPositionName()),
                application.getStatus(),
                accepting,
                editable,
                submittable,
                withdrawable,
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                completionSummary,
                List.copyOf(requiredMissingSections),
                List.copyOf(optionalIncompleteSections),
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
