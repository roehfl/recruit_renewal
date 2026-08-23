package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoticeSaveRequest(
        @NotBlank
        String title,
        String content,
        boolean isPinned
) {
}
