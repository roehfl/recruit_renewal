package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Purge execute 요청(Phase 09d-1, ADR-0007 안전장치).
 * bulk = {@code sourceDryRunBatchId}(필수, 근거 dry-run) / 단건 = {@code applicationId} — 정확히 하나만.
 * {@code confirm} 은 비가역 파기 실행의 명시적 확인 — true 가 아니면 거부.
 */
public record PurgeExecuteRequest(
        @NotNull(message = "confirm은 필수입니다.")
        Boolean confirm,

        Long sourceDryRunBatchId,

        Long applicationId
) {
}
