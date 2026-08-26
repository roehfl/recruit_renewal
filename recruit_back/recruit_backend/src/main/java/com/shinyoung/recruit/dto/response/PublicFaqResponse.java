package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Faq;

/**
 * 지원자 화면용 FAQ 항목. 노출에 필요한 최소 필드만 담는다(sortOrder/active 미노출).
 */
public record PublicFaqResponse(
        Long id,
        String question,
        String answer
) {
    public static PublicFaqResponse from(Faq faq) {
        return new PublicFaqResponse(faq.getId(), faq.getQuestion(), faq.getAnswer());
    }
}
