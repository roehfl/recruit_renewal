package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * FAQ 생성/수정 요청. answer 는 평문이며 줄바꿈만 보존한다(HTML 미지원).
 * sortOrder 는 서버가 관리한다(생성·카테고리 이동 시 자동 부여, 변경은 reorder 전용).
 */
public record FaqSaveRequest(
        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotBlank(message = "질문은(는) 필수입니다.")
        @Size(max = 500, message = "질문은 500자를 초과할 수 없습니다.")
        String question,

        @NotBlank(message = "답변은(는) 필수입니다.")
        String answer,

        Boolean active
) {
}
