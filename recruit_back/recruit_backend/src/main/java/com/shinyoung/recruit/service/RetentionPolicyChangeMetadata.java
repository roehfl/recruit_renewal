package com.shinyoung.recruit.service;

/** RetentionPolicy CUD 감사 metadata(PII-free — 정책 설정값만, Phase 09c). */
public record RetentionPolicyChangeMetadata(
        String operation,
        Long policyId,
        Long jobPostingId,
        Integer retentionPeriodDays,
        String baselineType,
        Boolean enabled
) implements AuditMetadata {
}
