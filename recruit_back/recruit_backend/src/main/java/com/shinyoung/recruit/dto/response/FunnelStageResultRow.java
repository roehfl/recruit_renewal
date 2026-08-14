package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.time.LocalDateTime;

/**
 * Funnel 통계 내부 projection: 모집단 P 지원서의 특정 stage 결과 1건(지원서×stage→결과상태).
 * 응답 DTO가 아니라 집계 입력이며, JPA 생성자 표현식으로 직접 조회된다.
 *
 * @param decidedAt 결과 확정 시각. 평균 체류일 산출의 기준시각이며, 미확정이면 null이다.
 */
public record FunnelStageResultRow(
        Long applicationId,
        Long stageId,
        StageResultStatus resultStatus,
        LocalDateTime decidedAt
) {
}
