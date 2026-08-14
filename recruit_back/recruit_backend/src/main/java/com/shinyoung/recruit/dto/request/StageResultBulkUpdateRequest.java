package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StageResultBulkUpdateRequest(
        @NotNull(message = "StageResult bulk update items are required.")
        @NotEmpty(message = "StageResult bulk update items are required.")
        List<@Valid StageResultBulkUpdateItemRequest> results
) {
}
