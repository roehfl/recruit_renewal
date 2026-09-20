package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.ForcedPurgeReason;
import jakarta.validation.constraints.NotNull;

/**
 * 강제 파기 요청(Phase 10). 사유는 선택형 enum 만 받는다 — 자유 텍스트는 감사 로그에 PII 를 남긴다.
 * {@code confirm} 은 비가역 파기의 명시적 확인.
 */
public record ForcedPurgeRequest(
        @NotNull(message = "applicantId는 필수입니다.")
        Long applicantId,

        @NotNull(message = "파기 사유는 필수입니다.")
        ForcedPurgeReason reasonCode,

        @NotNull(message = "confirm은 필수입니다.")
        Boolean confirm
) {
}
