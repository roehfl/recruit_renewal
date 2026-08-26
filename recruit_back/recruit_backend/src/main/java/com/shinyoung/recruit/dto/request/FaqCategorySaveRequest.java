package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FAQ 카테고리 생성/수정 요청. sortOrder 는 서버가 관리하므로 필드에 없다(생성 시 자동 부여, 변경은 reorder 전용).
 */
public record FaqCategorySaveRequest(
        @NotBlank(message = "카테고리명은(는) 필수입니다.")
        @Size(max = 100, message = "카테고리명은 100자를 초과할 수 없습니다.")
        String name,

        Boolean active
) {
}
