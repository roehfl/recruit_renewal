package com.shinyoung.recruit.dto.response;

import java.util.List;

public record StageResultInitializeResponse(
        Long stageId,
        int createdCount,
        int existingCount,
        int skippedCount,
        int removedCount,
        List<AdminStageResultResponse> results
) {
}
