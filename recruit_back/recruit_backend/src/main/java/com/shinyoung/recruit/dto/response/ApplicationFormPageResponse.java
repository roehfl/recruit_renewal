package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;

import java.time.LocalDateTime;
import java.util.List;

public record ApplicationFormPageResponse(
        Long applicationId,
        Long jobPostingId,
        String jobPostingTitle,
        JobPostingStatus jobPostingStatus,
        JobPostingType postingType,
        Long jobPositionId,
        String jobPositionName,
        /** 지원자가 선택한 근무지 코드. 선택하지 않았으면 null. */
        String workLocationCode,
        /** 지원자가 선택한 근무지 표시명. 선택하지 않았으면 null. */
        String workLocationName,
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
                jobPosting.getPostingType(),
                jobPosition.getId(),
                resolveText(application.getJobPositionNameSnapshot(), jobPosition.getPositionName()),
                application.getWorkLocationCode(),
                application.getWorkLocationNameSnapshot(),
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
