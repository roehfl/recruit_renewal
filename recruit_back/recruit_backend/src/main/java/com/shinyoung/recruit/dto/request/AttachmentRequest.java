package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AttachmentRequest(
        @NotNull(message = "Attachment type is required.")
        AttachmentType attachmentType,

        @NotNull(message = "Section type is required.")
        ApplicationSectionType sectionType,

        Long sectionRecordId,

        @NotBlank(message = "Original file name is required.")
        @Size(max = 255, message = "Original file name must be 255 characters or less.")
        String originalFileName,

        @Size(max = 255, message = "Stored file name must be 255 characters or less.")
        String storedFileName,

        @Size(max = 1000, message = "Storage path must be 1000 characters or less.")
        String storagePath,

        @NotBlank(message = "Content type is required.")
        @Size(max = 100, message = "Content type must be 100 characters or less.")
        String contentType,

        @NotNull(message = "File size is required.")
        @Positive(message = "File size must be greater than 0.")
        Long fileSize,

        @NotNull(message = "Sort order is required.")
        @Min(value = 0, message = "Sort order must be greater than or equal to 0.")
        Integer sortOrder
) {
}
