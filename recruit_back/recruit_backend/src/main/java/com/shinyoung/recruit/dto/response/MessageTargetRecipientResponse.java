package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 메시지 수신 대상 1명(지원서 1건). 연락처는 원문 그대로이며 형식이 맞을 때만 *Available 이 true 다.
 * resultStatus 는 결과 발표, interviewGroup·interviewDateTime 은 면접 2종,
 * draftStartedAt 은 마감 임박에서만(지원서 작성 시작 시각) 채운다.
 * variables 는 그 종류에 허용된 변수의 수신자별 값, missingVariables 는 값이 빈 변수 키다.
 */
public record MessageTargetRecipientResponse(
        Long applicationId,
        String name,
        String email,
        String phone,
        boolean mailAvailable,
        boolean smsAvailable,
        StageResultStatus resultStatus,
        String interviewGroup,
        LocalDateTime interviewDateTime,
        LocalDateTime draftStartedAt,
        Map<String, String> variables,
        List<String> missingVariables
) {
}
