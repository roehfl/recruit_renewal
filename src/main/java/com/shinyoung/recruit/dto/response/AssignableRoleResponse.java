package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.security.auth.RoleNames;

public record AssignableRoleResponse(String name, String label) {
    public static AssignableRoleResponse from(RoleNames.AssignableRole role) {
        return new AssignableRoleResponse(role.name(), role.label());
    }
}
