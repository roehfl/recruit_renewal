package com.shinyoung.recruit.dto.request;

/**
 * 면접 스케줄 업로드 파일의 한 행(파서 출력). 값은 모두 trim 된 문자열이며, 형식 검증은 service가 한다.
 *
 * @param rowNumber 엑셀 기준 1-based 행 번호(헤더 = 1행)
 * @param formula   수식 셀이 하나라도 있으면 true(행 오류)
 */
public record InterviewScheduleUploadRowRequest(
        int rowNumber,
        String date,
        String location,
        String arrivalTime,
        String interviewTime,
        String candidateOrder,
        String groupName,
        String interviewers,
        String applicationId,
        String applicantName,
        boolean formula
) {
}
