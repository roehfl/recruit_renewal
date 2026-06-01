package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;

/**
 * Funnel 통계 내부 projection: 모집단 P(제출 이력 보유 지원서) 한 건의 식별/상태/분야.
 * 응답 DTO가 아니라 집계 입력이며, JPA 생성자 표현식으로 직접 조회된다.
 *
 * <p>POSITION dimension은 운영 화면의 "분야별 통계"이므로, 표시명은 지원 당시 snapshot이 아니라 현재
 * {@code JobPosition.positionName}을, 정렬은 {@code JobPosition.sortOrder}를 사용한다.
 */
public record FunnelCohortRow(
        Long applicationId,
        JobApplicationStatus status,
        Long jobPositionId,
        String jobPositionName,
        Integer jobPositionSortOrder
) {
}
