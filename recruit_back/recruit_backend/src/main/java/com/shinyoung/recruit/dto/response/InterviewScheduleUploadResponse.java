package com.shinyoung.recruit.dto.response;

import java.util.List;

/**
 * 면접 스케줄 업로드 결과. 오류가 1건이라도 있으면 아무것도 반영하지 않고(all-or-nothing) 건수는 0이다.
 *
 * @param replacedInterviewCount 교체로 지운 기존 면접 수
 * @param errors                 행에 묶이지 않는 파일 단위 오류(조 번호 누락, 면접관 시각 중복 등)
 */
public record InterviewScheduleUploadResponse(
        Long stageId,
        int interviewCount,
        int candidateCount,
        int replacedInterviewCount,
        List<String> errors,
        List<InterviewScheduleUploadRowError> rowErrors
) {

    public static InterviewScheduleUploadResponse rejected(
            Long stageId,
            List<String> errors,
            List<InterviewScheduleUploadRowError> rowErrors
    ) {
        return new InterviewScheduleUploadResponse(stageId, 0, 0, 0, List.copyOf(errors), List.copyOf(rowErrors));
    }

    public boolean hasErrors() {
        return !errors.isEmpty() || !rowErrors.isEmpty();
    }
}
