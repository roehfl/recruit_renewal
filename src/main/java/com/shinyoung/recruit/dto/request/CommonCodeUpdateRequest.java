package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CommonCode 수정 요청. {@code groupCode}/{@code code} 는 불변이라 포함하지 않는다.
 * {@code active=false} 로 soft delete 한다.
 */
public record CommonCodeUpdateRequest(
        @NotBlank(message = "displayName은(는) 필수입니다.")
        @Size(max = 200, message = "displayName은(는) 200자 이하여야 합니다.")
        String displayName,

        Integer sortOrder,

        Boolean active,

        @Size(max = 500, message = "description은(는) 500자 이하여야 합니다.")
        String description
) {
}
