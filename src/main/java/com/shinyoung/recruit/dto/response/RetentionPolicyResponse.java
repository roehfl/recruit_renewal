package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;

import java.time.LocalDateTime;

public record RetentionPolicyResponse(
        Long id,
        Long jobPostingId,
        int retentionPeriodDays,
        RetentionBaselineType baselineType,
        boolean enabled,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo
) {
    public static RetentionPolicyResponse from(RetentionPolicy policy) {
        return new RetentionPolicyResponse(
                policy.getId(),
                policy.getJobPostingId(),
                policy.getRetentionPeriodDays(),
                policy.getBaselineType(),
                policy.isEnabled(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo()
        );
    }
}
