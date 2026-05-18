package com.shinyoung.recruit.dto.response;

import java.util.List;

public record StageResultInitializeResponse(
        Long stageId,
        int createdCount,
        int existingCount,
        int skippedCount,
        List<AdminStageResultResponse> results
) {
}
