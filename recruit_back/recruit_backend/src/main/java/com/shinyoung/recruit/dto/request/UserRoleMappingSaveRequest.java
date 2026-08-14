package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UserRoleMappingSaveRequest(
        @NotBlank
        String loginId,
        @NotBlank
        String roleName
) {
}
