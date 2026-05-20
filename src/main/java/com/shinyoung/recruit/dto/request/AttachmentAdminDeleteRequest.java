package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttachmentAdminDeleteRequest(
        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
