package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;

public record DeptRoleMappingResponse(Long id, String deptName, String roleName) {
    public static DeptRoleMappingResponse from(DeptRoleMapping mapping) {
        return new DeptRoleMappingResponse(mapping.getId(), mapping.getDeptName(), mapping.getRoleName());
    }
}
