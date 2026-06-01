package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.StageResult;

import java.time.format.DateTimeFormatter;

/**
 * upload-template sheet의 한 행(모든 컬럼 문자열). 현재 {@link StageResult} 값을 prefill하고,
 * {@code stageResultUpdatedAt}에는 낙관적 동시성 토큰(현재 updatedAt의 ISO-8601 문자열)을 넣는다.
 * 모든 컬럼은 writer가 string cell로 기록한다(토큰도 string cell).
 */
public record StageResultUploadTemplateRow(
        String stageResultId,
        String applicationId,
        String applicantName,
        String stageResultUpdatedAt,
        String resultStatus,
        String score,
        String comment
) {

    private static final DateTimeFormatter TOKEN_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static StageResultUploadTemplateRow from(StageResult result) {
        return new StageResultUploadTemplateRow(
                String.valueOf(result.getId()),
                String.valueOf(result.getJobApplication().getId()),
                result.getJobApplication().getApplicantNameSnapshot(),
                result.getUpdatedAt() == null ? "" : TOKEN_FORMAT.format(result.getUpdatedAt()),
                result.getResultStatus() == null ? "" : result.getResultStatus().name(),
                result.getScore() == null ? "" : result.getScore().toPlainString(),
                result.getComment() == null ? "" : result.getComment()
        );
    }
}
