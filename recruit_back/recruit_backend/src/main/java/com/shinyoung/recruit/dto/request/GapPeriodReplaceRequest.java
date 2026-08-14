package com.shinyoung.recruit.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GapPeriodReplaceRequest(
        @NotNull(message = "Gap period list is required.")
        List<@Valid GapPeriodRequest> gapPeriods
) {
}
