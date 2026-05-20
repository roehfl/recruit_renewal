package com.shinyoung.recruit.dto.response;

public record ApplicationCompletionSummaryResponse(
        int requiredSectionCount,
        int completedRequiredSectionCount,
        int requiredMissingCount,
        int optionalSectionCount,
        int completedOptionalSectionCount,
        int optionalIncompleteCount,
        int requiredCompletionRate,
        int submitBlockingIssueCount
) {
}
