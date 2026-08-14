package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageType;

/**
 * 한 stage의 funnel 결과: raw 분포(현황)와 순차 통과 집합(funnel 비율)을 분리해 노출한다.
 *
 * <p>{@code distribution}은 P 전체의 raw 7-bucket 분포(합=|P|)다. {@code funnelPassedCount}는 순차 통과
 * 집합 |S_k| = S_(k-1) ∩ {stage k 결과 PASSED}. 비율은 순차 통과 집합 기준이라 raw {@code distribution.passed}와
 * 값이 다를 수 있다: {@code cumulativeRate} = |S_k|/|P|, {@code stepConversionRate} = |S_k|/|S_(k-1)|(S0=P).
 *
 * @param averageDwellDays 이 stage의 평균 체류일(단계 간 소요일). 기준시각은 첫 stage면
 *                         {@code JobApplication.submittedAt}, 그 외에는 직전 stage의 {@code decidedAt}이다.
 *                         결과 미확정 건과 기준시각을 만들 수 없는 건은 표본에서 제외하며, 표본이 없으면
 *                         null이다("즉시 처리"와 "표본 없음"은 다르므로 0.0으로 채우지 않는다).
 */
public record StageFunnelResponse(
        Integer stageOrder,
        Long stageId,
        String stageName,
        StageType stageType,
        StageDistributionResponse distribution,
        long funnelPassedCount,
        double cumulativeRate,
        double stepConversionRate,
        Double averageDwellDays
) {
}
