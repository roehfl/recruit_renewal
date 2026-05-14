package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.StageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record StageUpdateRequest(
        @NotBlank String stageName,
        @NotNull StageType stageType,
        @NotNull @Min(0) Integer stageOrder,
        LocalDateTime resultAnnouncementDateTime,
        boolean finalStage
) {
}
