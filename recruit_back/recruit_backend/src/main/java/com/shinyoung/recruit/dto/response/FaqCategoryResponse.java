package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.FaqCategory;

/**
 * 관리자 FAQ 카테고리 응답. faqCount 는 해당 카테고리의 활성 FAQ 수다.
 */
public record FaqCategoryResponse(
        Long id,
        String name,
        Integer sortOrder,
        boolean active,
        int faqCount
) {
    public static FaqCategoryResponse from(FaqCategory category, int faqCount) {
        return new FaqCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSortOrder(),
                category.isActive(),
                faqCount
        );
    }
}
