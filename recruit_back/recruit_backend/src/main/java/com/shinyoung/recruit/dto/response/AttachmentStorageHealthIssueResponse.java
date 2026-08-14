package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.AttachmentStorageHealthIssueType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;

public record AttachmentStorageHealthIssueResponse(
        String category,
        Long applicationId,
        Long attachmentId,
        PhysicalFileStatus rowStatus,
        String fileKeyHash,
        Long physicalFileSize,
        String message
) {

    public static AttachmentStorageHealthIssueResponse of(
            AttachmentStorageHealthIssueType category,
            Long applicationId,
            Long attachmentId,
            PhysicalFileStatus rowStatus,
            String fileKeyHash,
            Long physicalFileSize,
            String message
    ) {
        return new AttachmentStorageHealthIssueResponse(
                category.name(),
                applicationId,
                attachmentId,
                rowStatus,
                fileKeyHash,
                physicalFileSize,
                message
        );
    }
}
