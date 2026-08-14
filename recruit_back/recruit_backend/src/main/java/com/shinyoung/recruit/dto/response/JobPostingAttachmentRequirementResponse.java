package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;

public record JobPostingAttachmentRequirementResponse(
        Long requirementId,
        Long jobPostingId,
        AttachmentType attachmentType,
        ApplicationSectionType sectionType,
        boolean required,
        int minCount,
        int sortOrder,
        String displayName,
        String description
) {

    public static JobPostingAttachmentRequirementResponse from(JobPostingAttachmentRequirement requirement) {
        return new JobPostingAttachmentRequirementResponse(
                requirement.getId(),
                requirement.getJobPosting().getId(),
                requirement.getAttachmentType(),
                requirement.getSectionType(),
                requirement.isRequired(),
                requirement.getMinCount(),
                requirement.getSortOrder(),
                requirement.getDisplayName(),
                requirement.getDescription()
        );
    }
}
