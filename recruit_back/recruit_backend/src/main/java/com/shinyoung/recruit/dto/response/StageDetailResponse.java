package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDateTime;

public record StageDetailResponse(
        Long id,
        Long jobPostingId,
        String stageName,
        StageType stageType,
        Integer stageOrder,
        StageStatus status,
        LocalDateTime resultAnnouncementDateTime,
        boolean finalStage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StageDetailResponse from(Stage stage) {
        return new StageDetailResponse(
                stage.getId(),
                stage.getJobPosting().getId(),
                stage.getStageName(),
                stage.getStageType(),
                stage.getStageOrder(),
                stage.getStatus(),
                stage.getResultAnnouncementDateTime(),
                stage.isFinalStage(),
                stage.getCreatedAt(),
                stage.getUpdatedAt()
        );
    }
}
