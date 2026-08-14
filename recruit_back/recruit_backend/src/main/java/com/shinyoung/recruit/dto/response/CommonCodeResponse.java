package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.CommonCode;

public record CommonCodeResponse(
        Long id,
        String groupCode,
        String code,
        String displayName,
        Integer sortOrder,
        boolean active,
        String description
) {

    public static CommonCodeResponse from(CommonCode commonCode) {
        return new CommonCodeResponse(
                commonCode.getId(),
                commonCode.getGroupCode(),
                commonCode.getCode(),
                commonCode.getDisplayName(),
                commonCode.getSortOrder(),
                commonCode.isActive(),
                commonCode.getDescription()
        );
    }
}
