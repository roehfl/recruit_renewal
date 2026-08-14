package com.shinyoung.recruit.dto.response;

/**
 * Funnel 모집단 P 요약. P = 제출 이력(submittedAt != null) 보유 지원서 코호트(현재 status 무관).
 *
 * @param p 모집단 크기 |P|
 * @param currentlySubmittedCount P 중 현재 status == SUBMITTED 수
 * @param withdrawnCount P 중 현재 status == WITHDRAWN 수(제출 후 철회). stage 분포의 withdrawn과 다르다.
 */
public record FunnelPopulationResponse(
        long p,
        long currentlySubmittedCount,
        long withdrawnCount
) {
}
