package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.StageResultCorrectionHistory;
import com.shinyoung.recruit.enumeration.StageResultStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StageResultCorrectionHistoryResponse(
        Long historyId,
        Long stageResultId,
        LocalDateTime correctedAt,
        String correctedBy,
        String reason,
        StageResultStatus previousStatus,
        StageResultStatus newStatus,
        BigDecimal previousScore,
        BigDecimal newScore,
        String previousComment,
        String newComment,
        LocalDateTime previousDecidedAt,
        LocalDateTime newDecidedAt
) {

    public static StageResultCorrectionHistoryResponse from(StageResultCorrectionHistory history) {
        return new StageResultCorrectionHistoryResponse(
                history.getId(),
                history.getStageResult().getId(),
                history.getCorrectedAt(),
                history.getCorrectedBy(),
                history.getReason(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getPreviousScore(),
                history.getNewScore(),
                history.getPreviousComment(),
                history.getNewComment(),
                history.getPreviousDecidedAt(),
                history.getNewDecidedAt()
        );
    }
}
