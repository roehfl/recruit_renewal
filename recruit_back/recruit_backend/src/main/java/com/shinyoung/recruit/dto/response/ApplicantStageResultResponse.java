package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;

public record ApplicantStageResultResponse(
        String stageName,
        StageType stageType,
        Integer stageOrder,
        StageResultStatus resultStatus,
        LocalDateTime resultAnnouncementDateTime,
        LocalDateTime decidedAt
) {

    public static ApplicantStageResultResponse from(StageResult stageResult) {
        Stage stage = stageResult.getStage();
        return new ApplicantStageResultResponse(
                stage.getStageName(),
                stage.getStageType(),
                stage.getStageOrder(),
                stageResult.getResultStatus(),
                stage.getResultAnnouncementDateTime(),
                stageResult.getDecidedAt()
        );
    }
}
