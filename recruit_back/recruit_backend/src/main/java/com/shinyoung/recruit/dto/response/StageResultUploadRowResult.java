package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultUploadRowStatus;

import java.util.List;

/**
 * upload 행 단위 검증/적용 결과. preview·commit 응답이 공유한다.
 *
 * @param rowNumber      스프레드시트 행 번호(1-based)
 * @param stageResultId  파싱된 매칭 키(파싱 실패 시 null)
 * @param applicationId  파싱된 교차검증 값(파싱 실패 시 null)
 * @param applicantName  사람 눈 확인용 echo
 * @param status         행 상태(CHANGED/UNCHANGED/ERROR/STALE)
 * @param errors         ERROR/STALE 사유(없으면 빈 리스트)
 * @param diff           CHANGED/STALE의 변경 전후 비교(없으면 null)
 */
public record StageResultUploadRowResult(
        int rowNumber,
        Long stageResultId,
        Long applicationId,
        String applicantName,
        StageResultUploadRowStatus status,
        List<String> errors,
        StageResultUploadDiff diff
) {

    public static StageResultUploadRowResult error(
            int rowNumber,
            Long stageResultId,
            Long applicationId,
            String applicantName,
            List<String> errors
    ) {
        return new StageResultUploadRowResult(
                rowNumber, stageResultId, applicationId, applicantName,
                StageResultUploadRowStatus.ERROR, List.copyOf(errors), null);
    }

    public static StageResultUploadRowResult unchanged(
            int rowNumber,
            Long stageResultId,
            Long applicationId,
            String applicantName
    ) {
        return new StageResultUploadRowResult(
                rowNumber, stageResultId, applicationId, applicantName,
                StageResultUploadRowStatus.UNCHANGED, List.of(), null);
    }

    public static StageResultUploadRowResult changed(
            int rowNumber,
            Long stageResultId,
            Long applicationId,
            String applicantName,
            StageResultUploadDiff diff
    ) {
        return new StageResultUploadRowResult(
                rowNumber, stageResultId, applicationId, applicantName,
                StageResultUploadRowStatus.CHANGED, List.of(), diff);
    }

    public static StageResultUploadRowResult stale(
            int rowNumber,
            Long stageResultId,
            Long applicationId,
            String applicantName,
            String currentToken,
            StageResultUploadDiff diff
    ) {
        return new StageResultUploadRowResult(
                rowNumber, stageResultId, applicationId, applicantName,
                StageResultUploadRowStatus.STALE,
                List.of("STALE_ROW: 다른 사용자가 변경했습니다. 현재 토큰=" + currentToken),
                diff);
    }
}
