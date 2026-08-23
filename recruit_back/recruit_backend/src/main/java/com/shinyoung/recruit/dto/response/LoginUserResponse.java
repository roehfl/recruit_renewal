package com.shinyoung.recruit.dto.response;

import java.util.List;

public record LoginUserResponse(
        String loginId,
        String name,
        String deptName,
        String userType,
        List<String> roles
) {
}
