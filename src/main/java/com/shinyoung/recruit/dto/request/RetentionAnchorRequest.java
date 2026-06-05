package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** JobPosting.hiringEndedAt 수동 확정 요청(Phase 09c — 자동 세팅 금지, 설계 §5.3). */
public record RetentionAnchorRequest(
        @NotNull(message = "hiringEndedAt은 필수입니다.")
        LocalDateTime hiringEndedAt
) {
}
