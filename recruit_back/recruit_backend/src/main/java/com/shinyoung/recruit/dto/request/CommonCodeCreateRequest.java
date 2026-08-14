package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CommonCode 생성 요청. {@code groupCode}/{@code code} 는 생성 시 확정되며, code 는 이후 불변이다.
 * {@code sortOrder} 미지정 시 0, {@code active} 미지정 시 true.
 */
public record CommonCodeCreateRequest(
        @NotBlank(message = "groupCode은(는) 필수입니다.")
        @Size(max = 100, message = "groupCode은(는) 100자 이하여야 합니다.")
        String groupCode,

        @NotBlank(message = "code은(는) 필수입니다.")
        @Size(max = 100, message = "code은(는) 100자 이하여야 합니다.")
        String code,

        @NotBlank(message = "displayName은(는) 필수입니다.")
        @Size(max = 200, message = "displayName은(는) 200자 이하여야 합니다.")
        String displayName,

        Integer sortOrder,

        Boolean active,

        @Size(max = 500, message = "description은(는) 500자 이하여야 합니다.")
        String description
) {
}
