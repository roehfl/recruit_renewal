package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * FAQ 카테고리 정렬 일괄 반영 요청. ids 순서가 곧 노출 순서다(0..n-1 로 정규화).
 */
public record FaqCategoryReorderRequest(
        @NotEmpty(message = "정렬 대상 목록은(는) 필수입니다.")
        List<Long> ids
) {
}
