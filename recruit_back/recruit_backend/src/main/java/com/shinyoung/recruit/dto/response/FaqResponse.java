package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Faq;

/**
 * 관리자 FAQ 단건 응답. answer 는 평문이다.
 */
public record FaqResponse(
        Long id,
        Long categoryId,
        String question,
        String answer,
        Integer sortOrder,
        boolean active
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getCategory().getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getSortOrder(),
                faq.isActive()
        );
    }
}
