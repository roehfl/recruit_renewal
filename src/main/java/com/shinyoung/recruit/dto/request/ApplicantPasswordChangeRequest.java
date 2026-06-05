package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicantPasswordChangeRequest(
        @NotBlank(message = "currentPassword는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "newPassword는 필수입니다.")
        @Size(min = 8, max = 100, message = "newPassword는 8자 이상 100자 이하여야 합니다.")
        String newPassword
) {
}
