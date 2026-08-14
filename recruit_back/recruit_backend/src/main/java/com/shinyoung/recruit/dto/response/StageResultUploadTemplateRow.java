package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.StageResult;

/**
 * upload-template sheet의 한 행(모든 컬럼 문자열). 현재 {@link StageResult} 값을 prefill하고,
 * {@code stageResultUpdatedAt}에는 낙관적 동시성 토큰(현재 updatedAt을 normalize한 ISO-8601 문자열)을 넣는다.
 * 토큰은 commit 비교와 동일 규칙을 쓰기 위해 {@code StageResultUploadService}에서 만들어 주입한다.
 * 모든 컬럼은 round-trip writer가 변형 없이 string cell로 기록한다(토큰도 string cell).
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
}
