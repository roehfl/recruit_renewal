package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 한 카테고리 안의 FAQ 정렬 일괄 반영 요청. ids 순서가 곧 노출 순서다(0..n-1 로 정규화).
 */
public record FaqReorderRequest(
        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotEmpty(message = "정렬 대상 목록은(는) 필수입니다.")
        List<Long> ids
) {
}
