package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;

public record AdminAttachmentResponse(
        Long attachmentId,
        AttachmentType attachmentType,
        ApplicationSectionType sectionType,
        Long sectionRecordId,
        String originalFileName,
        String contentType,
        Long fileSize,
        Integer sortOrder
) {

    public static AdminAttachmentResponse from(ApplicationAttachment attachment) {
        return new AdminAttachmentResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getSectionType(),
                attachment.getSectionRecordId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getSortOrder()
        );
    }
}
