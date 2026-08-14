package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * dimension breakdown의 한 그룹별 funnel(예: 분야 1개). 그룹 코호트는 모집단 P를 그룹 키로 분할한 부분집합이며,
 * 모든 dimension은 지원서(application) 단위 distinct로 센다.
 *
 * @param groupId   그룹 식별자(POSITION이면 jobPositionId)
 * @param groupName 그룹 표시명(POSITION이면 분야명 snapshot)
 */
public record DimensionFunnelResponse(
        Long groupId,
        String groupName,
        FunnelPopulationResponse population,
        List<StageFunnelResponse> stages
) {
}
