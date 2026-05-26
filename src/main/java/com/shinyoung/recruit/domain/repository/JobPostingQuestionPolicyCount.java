package com.shinyoung.recruit.domain.repository;

public record JobPostingQuestionPolicyCount(
        Long jobPostingId,
        long activeQuestionCount,
        long requiredQuestionCount
) {

    public JobPostingQuestionPolicyCount(Long jobPostingId, Long activeQuestionCount, Long requiredQuestionCount) {
        this(jobPostingId, valueOrZero(activeQuestionCount), valueOrZero(requiredQuestionCount));
    }

    public static JobPostingQuestionPolicyCount empty(Long jobPostingId) {
        return new JobPostingQuestionPolicyCount(jobPostingId, 0, 0);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }
}
