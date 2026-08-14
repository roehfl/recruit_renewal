package com.shinyoung.recruit.dto.response;

import java.util.List;

public record StageResultBulkUpdateResponse(
        Long stageId,
        int updatedCount,
        List<AdminStageResultResponse> results
) {
}
