package com.shinyoung.recruit.service;

public record AttachmentStorageDeleteResult(
        boolean requested,
        boolean deleted,
        boolean existed,
        boolean failed,
        String failureCode,
        String message
) {

    public static AttachmentStorageDeleteResult deletedFile() {
        return new AttachmentStorageDeleteResult(
                true,
                true,
                true,
                false,
                null,
                "Attachment file was deleted."
        );
    }

    public static AttachmentStorageDeleteResult absentFile() {
        return new AttachmentStorageDeleteResult(
                true,
                false,
                false,
                false,
                null,
                "Attachment file was already absent."
        );
    }

    public static AttachmentStorageDeleteResult invalidPath() {
        return new AttachmentStorageDeleteResult(
                true,
                false,
                false,
                true,
                "INVALID_STORAGE_PATH",
                "Attachment storage path is invalid."
        );
    }

    public static AttachmentStorageDeleteResult failed(String failureCode, String message) {
        return new AttachmentStorageDeleteResult(
                true,
                false,
                false,
                true,
                failureCode,
                message
        );
    }
}
