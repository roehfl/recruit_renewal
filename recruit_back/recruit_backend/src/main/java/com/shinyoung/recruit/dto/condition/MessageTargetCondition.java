package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.StageResultStatus;

/**
 * 메시지 대상 조건. null 은 "조건 없음": resultStatus null = 판정된 결과 전체, interviewGroup null = 전체 조,
 * applicationStatus null = 작성 중+제출. 종류별 필수 여부는 MessageTargetService 가 검증한다.
 */
public record MessageTargetCondition(
        MessageType type,
        Long jobPostingId,
        Long stageId,
        StageResultStatus resultStatus,
        String interviewGroup,
        JobApplicationStatus applicationStatus
) {
}
