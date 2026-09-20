package com.shinyoung.recruit.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파기 대상자 상세(Phase 10). 지원서별 적격성 판정을 함께 준다 — 화면은 이 값 하나로
 * "진행 중"(APPLICATION_NOT_TERMINAL)·"보류"(RETENTION_HOLD)·"이미 파기"(ALREADY_PURGED)를 표시한다.
 * hold 사유 원문은 주지 않는다(민감 자유 텍스트).
 */
public record DataSubjectDetailResponse(
        Long applicantId,
        String name,
        String email,
        String phoneNumber,
        boolean hasActiveHold,
        List<ApplicationRow> applications
) {
    public record ApplicationRow(
            Long applicationId,
            String jobPostingTitle,
            String status,
            LocalDateTime submittedAt,
            String purgeResult,
            boolean eligible,
            String reasonCode
    ) {
    }
}
