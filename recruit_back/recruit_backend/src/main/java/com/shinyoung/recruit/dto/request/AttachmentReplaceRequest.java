package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AttachmentReplaceRequest(
        // 폐지(⛔) API지만 살아 있으므로 크기를 막는다. 상한은 지원서당 첨부 기본 한도(20)와 같다.
        @NotNull(message = "Attachment list is required.")
        @Size(max = 20, message = "Attachment list must be 20 items or less.")
        List<@Valid AttachmentRequest> attachments
) {
}
