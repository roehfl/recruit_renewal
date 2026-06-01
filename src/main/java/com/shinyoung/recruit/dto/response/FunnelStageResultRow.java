package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultStatus;

/**
 * Funnel 통계 내부 projection: 모집단 P 지원서의 특정 stage 결과 1건(지원서×stage→결과상태).
 * 응답 DTO가 아니라 집계 입력이며, JPA 생성자 표현식으로 직접 조회된다.
 */
public record FunnelStageResultRow(
        Long applicationId,
        Long stageId,
        StageResultStatus resultStatus
) {
}
