package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeptRoleMappingSaveRequest(
        @NotBlank
        String deptName,
        @NotBlank
        String roleName
) {
}
