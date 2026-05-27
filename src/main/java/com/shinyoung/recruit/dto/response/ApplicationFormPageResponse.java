package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationFormPageResponse(
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        JobPostingStatus jobPostingStatus,
        Long jobPositionId,
        String jobPositionName,
        JobApplicationStatus applicationStatus,
        LocalDateTime receptionStartDateTime,
        LocalDateTime receptionEndDateTime,
        boolean accepting,
        boolean editable,
        LocalDateTime submittedAt,
        LocalDateTime withdrawnAt,
        ApplicationFormConfigResponse formConfig,
        List<ApplicationFormSectionResponse> sections
) {
    public static ApplicationFormPageResponse from(
            JobApplication application,
            boolean accepting,
            boolean editable,
            ApplicationFormConfig formConfig,
            List<ApplicationFormSectionResponse> sections
    ) {
        JobPosting jobPosting = application.getJobPosting();
        JobPosition jobPosition = application.getJobPosition();
        return new ApplicationFormPageResponse(
                application.getId(),
                jobPosting.getId(),
                resolveText(application.getJobPostingTitleSnapshot(), jobPosting.getTitle()),
                jobPosting.getStatus(),
                jobPosition.getId(),
                resolveText(application.getJobPositionNameSnapshot(), jobPosition.getPositionName()),
                application.getStatus(),
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                accepting,
                editable,
                application.getSubmittedAt(),
                application.getWithdrawnAt(),
                ApplicationFormConfigResponse.from(formConfig),
                List.copyOf(sections)
        );
    }

    private static String resolveText(String snapshot, String fallback) {
        if (snapshot != null && !snapshot.isBlank()) {
            return snapshot;
        }
        return fallback;
    }
}
