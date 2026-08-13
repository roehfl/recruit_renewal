package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.User;
import com.shinyoung.recruit.domain.entity.UserRoleMapping;

/**
 * 사용자별 role 매핑 응답. {@code userName}/{@code userDeptName}은 loginId가 users에 존재할 때만
 * 채워지는 참고 표시용이다(없으면 null — 최초 로그인 전 직원은 JIT 미생성이라 정상 상황).
 */
public record UserRoleMappingResponse(
        Long id,
        String loginId,
        String roleName,
        String userName,
        String userDeptName
) {
    public static UserRoleMappingResponse from(UserRoleMapping mapping, User user) {
        String userName = user != null ? user.getName() : null;
        String userDeptName = user instanceof Employee employee ? employee.getDeptName() : null;
        return new UserRoleMappingResponse(mapping.getId(), mapping.getLoginId(), mapping.getRoleName(), userName, userDeptName);
    }
}
