package com.shinyoung.recruit.domain.repository;

public record JobPostingAttachmentRequirementPolicyCount(
        Long jobPostingId,
        long totalRequirementCount,
        long requiredRequirementCount
) {

    public JobPostingAttachmentRequirementPolicyCount(
            Long jobPostingId,
            Long totalRequirementCount,
            Long requiredRequirementCount
    ) {
        this(jobPostingId, valueOrZero(totalRequirementCount), valueOrZero(requiredRequirementCount));
    }

    public static JobPostingAttachmentRequirementPolicyCount empty(Long jobPostingId) {
        return new JobPostingAttachmentRequirementPolicyCount(jobPostingId, 0, 0);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
