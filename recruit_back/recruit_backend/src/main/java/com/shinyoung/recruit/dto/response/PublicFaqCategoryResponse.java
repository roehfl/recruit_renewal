package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.FaqCategory;

import java.util.List;

/**
 * 지원자 화면용 카테고리 + 그 안의 FAQ 목록. 노출 가능한 FAQ 가 없는 카테고리는 서비스에서 제외한다.
 */
public record PublicFaqCategoryResponse(
        Long id,
        String name,
        List<PublicFaqResponse> faqs
) {
    public static PublicFaqCategoryResponse of(FaqCategory category, List<PublicFaqResponse> faqs) {
        return new PublicFaqCategoryResponse(category.getId(), category.getName(), faqs);
    }
}
