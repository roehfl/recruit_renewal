package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.math.BigDecimal;

/**
 * CHANGED/STALE 행의 변경 전(현재 DB 값)·후(업로드 값) 비교. 사람 눈 확인 + 프론트 diff 표시용 문자열이다.
 */
public record StageResultUploadDiff(
        String oldResultStatus,
        String newResultStatus,
        String oldScore,
        String newScore,
        String oldComment,
        String newComment
) {

    public static StageResultUploadDiff of(
            StageResult current,
            StageResultStatus newStatus,
            BigDecimal newScore,
            String newComment
    ) {
        return new StageResultUploadDiff(
                current.getResultStatus() == null ? null : current.getResultStatus().name(),
                newStatus == null ? null : newStatus.name(),
                current.getScore() == null ? null : current.getScore().toPlainString(),
                newScore == null ? null : newScore.toPlainString(),
                current.getComment(),
                newComment
        );
    }
}
