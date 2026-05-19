package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.StageResultStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StageResultCorrectionRequest(
        @NotNull(message = "StageResult status is required.")
        StageResultStatus resultStatus,

        BigDecimal score,

        @Size(max = 2000, message = "StageResult comment must be 2000 characters or less.")
        String comment,

        @NotBlank(message = "Correction reason is required.")
        @Size(max = 1000, message = "Correction reason must be 1000 characters or less.")
        String reason
) {
}
