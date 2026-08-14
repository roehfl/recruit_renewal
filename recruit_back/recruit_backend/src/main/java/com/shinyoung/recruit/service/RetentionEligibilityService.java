package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파기 적격성(eligibility) 판정(Phase 09c, 설계 §5.3).
 *
 * <p>eligibility = anchor 종료 + retentionPeriod 경과 + not purged + not hold + terminal.
 * 부적격은 {@link AuditReasonCode} 로 분류한다. 판정 순서는 아래로 고정(결정적 계약):
 * ① ALREADY_PURGED ② RETENTION_HOLD ③ POLICY_NOT_FOUND/POLICY_CONFLICT ④ ANCHOR_NOT_FIXED
 * ⑤ RETENTION_NOT_DUE ⑥ INVALID_STAGE_CONFIGURATION ⑦ APPLICATION_NOT_TERMINAL.
 *
 * <p>terminal 판정은 설계 9c 확정 query 그대로(구현자 임의 해석 금지):
 * {@code status == WITHDRAWN} OR (finalStage 정확히 1개 AND finalStage.status IN
 * (RESULT_ANNOUNCED, CLOSED) AND 해당 application 의 StageResult 존재 AND resultStatus != PENDING
 * AND decidedAt != null).
 *
 * <p>날짜 계산은 호출자가 주입한 {@code scanAt}(Clock 산출) 기준 — 이 컴포넌트는 시계를 직접 읽지 않는다
 * (순수 판정, fixed Clock 테스트 용이).
 */
@Component
public class RetentionEligibilityService {

    /** 판정 결과 — {@code reasonCode == null} 이면 eligible. */
    public record EligibilityDecision(AuditReasonCode reasonCode) {
        public boolean eligible() {
            return reasonCode == null;
        }
    }

    public EligibilityDecision evaluate(
            JobApplication application,
            JobPosting jobPosting,
            RetentionPolicySelection policySelection,
            boolean activeHold,
            List<Stage> finalStages,
            StageResult finalStageResult,
            LocalDateTime scanAt
    ) {
        // ① 파기 진행/완료 marker 가 있으면 idempotent skip(설계 §5.4).
        if (application.getPurgeResult() != null) {
            return new EligibilityDecision(AuditReasonCode.ALREADY_PURGED);
        }
        // ② manual hold.
        if (activeHold) {
            return new EligibilityDecision(AuditReasonCode.RETENTION_HOLD);
        }
        // ③ 적용 정책 부재/충돌(fail-safe).
        if (!policySelection.selected()) {
            return new EligibilityDecision(policySelection.reasonCode());
        }
        // ④ anchor — 암묵 fallback 금지: 정책이 명시한 baseline 의 값만 본다.
        LocalDateTime anchor = switch (policySelection.policy().getBaselineType()) {
            case HIRING_ENDED_AT -> jobPosting.getHiringEndedAt();
            case CLOSED_AT -> jobPosting.getClosedAt();
        };
        if (anchor == null) {
            return new EligibilityDecision(AuditReasonCode.ANCHOR_NOT_FIXED);
        }
        // ⑤ retention 경과: anchor + period <= scanAt 이어야 due.
        if (anchor.plusDays(policySelection.policy().getRetentionPeriodDays()).isAfter(scanAt)) {
            return new EligibilityDecision(AuditReasonCode.RETENTION_NOT_DUE);
        }
        // ⑥/⑦ terminal.
        if (application.getStatus() == JobApplicationStatus.WITHDRAWN) {
            return new EligibilityDecision(null);
        }
        if (finalStages.size() != 1) {
            return new EligibilityDecision(AuditReasonCode.INVALID_STAGE_CONFIGURATION);
        }
        Stage finalStage = finalStages.get(0);
        boolean finalStageSettled = finalStage.getStatus() == StageStatus.RESULT_ANNOUNCED
                || finalStage.getStatus() == StageStatus.CLOSED;
        if (!finalStageSettled
                || finalStageResult == null
                || finalStageResult.getResultStatus() == StageResultStatus.PENDING
                || finalStageResult.getDecidedAt() == null) {
            return new EligibilityDecision(AuditReasonCode.APPLICATION_NOT_TERMINAL);
        }
        return new EligibilityDecision(null);
    }
}
