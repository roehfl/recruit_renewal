package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;

/**
 * 파기 대상자 검색 결과 행(Phase 10). 관리자 화면 규칙에 따라 이메일·휴대폰을 마스킹하지 않는다
 * (마스킹은 로그·감사 metadata 에만 적용).
 */
public record DataSubjectSummaryResponse(
        Long applicantId,
        String name,
        String email,
        String phoneNumber,
        long applicationCount,
        long purgedApplicationCount,
        LocalDateTime lastAppliedAt,
        boolean hasActiveHold
) {
}
