package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.FunnelDimension;

import java.util.List;

/**
 * 공고 단위 전형 funnel 통계 응답.
 *
 * <p>최상위에는 항상 overall(공고 전체 P) funnel({@code population} + {@code stages})을 담는다.
 * {@code dimension}이 지정되면 그 축의 그룹별 funnel을 {@code dimensions}에 추가로 담는다(미지정 시 빈 리스트).
 * statistics는 집계값만 노출하며 개별 식별 데이터/audit를 남기지 않는다.
 */
public record FunnelResponse(
        Long jobPostingId,
        String jobPostingTitle,
        FunnelDimension dimension,
        FunnelPopulationResponse population,
        List<StageFunnelResponse> stages,
        List<DimensionFunnelResponse> dimensions
) {
}
