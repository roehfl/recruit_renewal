package com.shinyoung.recruit.dto.response;

public record AttachmentDeleteResponse(
        Long applicationId,
        Long attachmentId,
        boolean deleted,
        boolean physicalDeleteRequested,
        String message
) {

    public static AttachmentDeleteResponse deleted(
            Long applicationId,
            Long attachmentId,
            boolean physicalDeleteRequested
    ) {
        return new AttachmentDeleteResponse(
                applicationId,
                attachmentId,
                true,
                physicalDeleteRequested,
                "Attachment was deleted."
        );
    }
}
