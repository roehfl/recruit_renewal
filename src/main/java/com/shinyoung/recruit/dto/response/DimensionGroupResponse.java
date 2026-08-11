package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.FunnelDimension;

import java.util.List;

/**
 * 한 dimension 축의 breakdown 묶음. 대시보드가 여러 축(POSITION·SCHOOL·CERTIFICATE)을 한 번에 요청할 때
 * 각 그룹이 어느 축 소속인지 구분하기 위해 축 단위로 감싼다.
 *
 * @param dimension 이 묶음의 축
 * @param groups    해당 축의 그룹별 funnel. 축별 정렬·topN·'기타' 규칙은 각 축의 산출 규칙을 따른다.
 */
public record DimensionGroupResponse(
        FunnelDimension dimension,
        List<DimensionFunnelResponse> groups
) {
}
