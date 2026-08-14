package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import com.shinyoung.recruit.enumeration.AuditReasonCode;

/**
 * scanAt 시점의 적용 정책 선택 결과(Phase 09c, 설계 §5.3 선택 규칙).
 * {@code policy != null} 이면 선택 성공, 아니면 {@code reasonCode} 가 {@code POLICY_NOT_FOUND} 또는
 * fail-safe {@code POLICY_CONFLICT}(active 후보 2개 이상 — 아무것도 선택하지 않음, 리뷰 3차 #4).
 */
public record RetentionPolicySelection(
        RetentionPolicy policy,
        AuditReasonCode reasonCode
) {
    public static RetentionPolicySelection found(RetentionPolicy policy) {
        return new RetentionPolicySelection(policy, null);
    }

    public static RetentionPolicySelection notFound() {
        return new RetentionPolicySelection(null, AuditReasonCode.POLICY_NOT_FOUND);
    }

    public static RetentionPolicySelection conflict() {
        return new RetentionPolicySelection(null, AuditReasonCode.POLICY_CONFLICT);
    }

    public boolean selected() {
        return policy != null;
    }
}
