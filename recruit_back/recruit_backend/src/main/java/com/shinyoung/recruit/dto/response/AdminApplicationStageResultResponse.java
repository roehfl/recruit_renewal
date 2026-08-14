package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminApplicationStageResultResponse(
        Long stageId,
        String stageName,
        StageType stageType,
        Integer stageOrder,
        StageStatus stageStatus,
        Boolean finalStage,
        LocalDateTime resultAnnouncementDateTime,
        Long stageResultId,
        StageResultStatus resultStatus,
        BigDecimal score,
        String comment,
        LocalDateTime decidedAt
) {

    public static AdminApplicationStageResultResponse of(Stage stage, StageResult stageResult) {
        return new AdminApplicationStageResultResponse(
                stage.getId(),
                stage.getStageName(),
                stage.getStageType(),
                stage.getStageOrder(),
                stage.getStatus(),
                stage.isFinalStage(),
                stage.getResultAnnouncementDateTime(),
                stageResult == null ? null : stageResult.getId(),
                stageResult == null ? null : stageResult.getResultStatus(),
                stageResult == null ? null : stageResult.getScore(),
                stageResult == null ? null : stageResult.getComment(),
                stageResult == null ? null : stageResult.getDecidedAt()
        );
    }
}
