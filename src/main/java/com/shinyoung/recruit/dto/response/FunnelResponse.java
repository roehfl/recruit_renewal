package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.FunnelDimension;

import java.util.List;

/**
 * 공고 단위 전형 funnel 통계 응답.
 *
 * <p>최상위에는 항상 overall(공고 전체 P) funnel({@code population} + {@code stages})을 담는다.
 * 요청한 dimension 축들의 그룹별 funnel은 {@code dimensionGroups}에 축 단위로 담긴다(미지정 시 빈 리스트).
 * statistics는 집계값만 노출하며 개별 식별 데이터/audit를 남기지 않는다.
 *
 * @param dimension  <b>@deprecated</b> 단일 축 요청일 때만 채워지는 하위호환 필드. 다중 축 요청이면 null이다.
 *                   신규 소비자는 {@code dimensionGroups}를 사용한다.
 * @param dimensions <b>@deprecated</b> 위와 같다. 다중 축 요청이면 빈 리스트다.
 *                   {@code dimensionGroups}의 해당 축 groups와 동일한 내용이다.
 */
public record FunnelResponse(
        Long jobPostingId,
        String jobPostingTitle,
        @Deprecated FunnelDimension dimension,
        FunnelPopulationResponse population,
        List<StageFunnelResponse> stages,
        @Deprecated List<DimensionFunnelResponse> dimensions,
        List<DimensionGroupResponse> dimensionGroups
) {
}
