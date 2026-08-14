package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;

public record AttachmentRequirementRequest(
        AttachmentType attachmentType,
        ApplicationSectionType sectionType,
        Boolean required,
        Integer minCount,
        Integer sortOrder,
        String displayName,
        String description
) {
}
