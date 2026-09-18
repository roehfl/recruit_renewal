package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

/**
 * 면접 스케줄 조회·다운로드 조건. {@code stageId} 는 필수이고 나머지는 지원자 행에 거는 선택 필터다.
 *
 * @param workLocation 근무지 코드(지원서의 {@code workLocationCode})
 * @param groupName    조. 정확히 일치하는 행만 남긴다
 */
public record InterviewScheduleSearchRequest(
        Long stageId,
        JobPositionApplicationType applicationType,
        Long jobPositionId,
        String workLocation,
        String groupName
) {
}
