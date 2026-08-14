package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;

public record AttachmentRequirementPublicResponse(
        AttachmentType attachmentType,
        ApplicationSectionType sectionType,
        boolean required,
        int minCount,
        int sortOrder,
        String displayName,
        String description
) {

    public static AttachmentRequirementPublicResponse from(JobPostingAttachmentRequirement requirement) {
        return new AttachmentRequirementPublicResponse(
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
