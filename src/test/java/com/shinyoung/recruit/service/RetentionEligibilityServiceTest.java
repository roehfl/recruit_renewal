package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파기 적격성 판정 단위 테스트(Phase 09c) — 설계 §5.3 terminal query/reasonCode 계약을 고정한다.
 * 순수 판정 컴포넌트라 DB/Clock 없이 fixed scanAt 으로 검증한다.
 */
class RetentionEligibilityServiceTest {

    private static final LocalDateTime SCAN_AT = LocalDateTime.of(2026, 6, 5, 12, 0);

    private final RetentionEligibilityService service = new RetentionEligibilityService();

    private JobPosting posting() {
        JobPosting posting = JobPosting.create("t", "<p>c</p>",
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 31, 18, 0));
        // StageResult.initialize 는 stage/application 의 jobPosting id 일치를 요구한다 — 비영속 fixture 에 id 부여.
        try {
            var idField = JobPosting.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(posting, 1L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return posting;
    }

    private JobApplication application(JobPosting posting) {
        Applicant applicant = new Applicant("elig-ci", HashUtil.sha256("elig-ci"));
        JobPosition position = JobPosition.create("Backend", 1);
        JobApplication application = JobApplication.create(applicant, posting, position, "name", "title", "pos");
        application.submit(LocalDateTime.of(2025, 1, 10, 10, 0));
        return application;
    }

    private RetentionPolicySelection policy(RetentionBaselineType baselineType) {
        return RetentionPolicySelection.found(
                RetentionPolicy.create(null, 30, baselineType, true, null, null));
    }

    private Stage announcedFinalStage(JobPosting posting) {
        Stage stage = Stage.create(posting, "final", StageType.DOCUMENT, 0, null, true);
        stage.start();
        stage.announce();
        return stage;
    }

    private StageResult decidedResult(Stage stage, JobApplication application) {
        StageResult result = StageResult.initialize(stage, application);
        result.updateResult(StageResultStatus.PASSED, null, null, LocalDateTime.of(2025, 2, 1, 10, 0), "emp01");
        return result;
    }

    @Test
    void 최종전형_확정이면_eligible() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);
        Stage finalStage = announcedFinalStage(posting);

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(finalStage), decidedResult(finalStage, application), SCAN_AT);

        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void WITHDRAWN은_finalStage_없어도_terminal() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);
        application.withdraw(LocalDateTime.of(2025, 1, 20, 10, 0));

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(), null, SCAN_AT);

        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void purgeResult가_있으면_ALREADY_PURGED() throws Exception {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);
        var field = JobApplication.class.getDeclaredField("purgeResult");
        field.setAccessible(true);
        field.set(application, PurgeResult.PURGED);

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(), null, SCAN_AT);

        assertThat(decision.reasonCode()).isEqualTo(AuditReasonCode.ALREADY_PURGED);
    }

    @Test
    void active_hold면_RETENTION_HOLD() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                true, List.of(), null, SCAN_AT);

        assertThat(decision.reasonCode()).isEqualTo(AuditReasonCode.RETENTION_HOLD);
    }

    @Test
    void 정책_부재_충돌은_그대로_SKIP_사유가_된다() {
        JobPosting posting = posting();
        JobApplication application = application(posting);

        assertThat(service.evaluate(application, posting, RetentionPolicySelection.notFound(),
                false, List.of(), null, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.POLICY_NOT_FOUND);
        assertThat(service.evaluate(application, posting, RetentionPolicySelection.conflict(),
                false, List.of(), null, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.POLICY_CONFLICT);
    }

    @Test
    void anchor_미확정이면_ANCHOR_NOT_FIXED_암묵_fallback_금지() {
        JobPosting posting = posting();
        posting.close(SCAN_AT.minusDays(100)); // closedAt 이 있어도 HIRING_ENDED_AT 정책은 fallback 하지 않는다.
        JobApplication application = application(posting);

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(), null, SCAN_AT);

        assertThat(decision.reasonCode()).isEqualTo(AuditReasonCode.ANCHOR_NOT_FIXED);
    }

    @Test
    void CLOSED_AT_정책은_closedAt을_anchor로_쓴다() {
        JobPosting posting = posting();
        posting.close(SCAN_AT.minusDays(100));
        JobApplication application = application(posting);
        application.withdraw(LocalDateTime.of(2025, 1, 20, 10, 0));

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.CLOSED_AT),
                false, List.of(), null, SCAN_AT);

        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void 보존기간_미경과면_RETENTION_NOT_DUE() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(10)); // period 30 — 미경과.
        JobApplication application = application(posting);

        var decision = service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(), null, SCAN_AT);

        assertThat(decision.reasonCode()).isEqualTo(AuditReasonCode.RETENTION_NOT_DUE);
    }

    @Test
    void finalStage가_0개_또는_2개면_INVALID_STAGE_CONFIGURATION() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);

        assertThat(service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(), null, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.INVALID_STAGE_CONFIGURATION);

        Stage first = announcedFinalStage(posting);
        Stage second = announcedFinalStage(posting);
        assertThat(service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(first, second), null, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.INVALID_STAGE_CONFIGURATION);
    }

    @Test
    void 결과_미확정이면_APPLICATION_NOT_TERMINAL() {
        JobPosting posting = posting();
        posting.fixHiringEndedAt(SCAN_AT.minusDays(60));
        JobApplication application = application(posting);

        // finalStage 가 아직 IN_PROGRESS.
        Stage inProgress = Stage.create(posting, "final", StageType.DOCUMENT, 0, null, true);
        inProgress.start();
        assertThat(service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(inProgress), decidedResult(inProgress, application), SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.APPLICATION_NOT_TERMINAL);

        // 결과 자체가 없다.
        Stage announced = announcedFinalStage(posting);
        assertThat(service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(announced), null, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.APPLICATION_NOT_TERMINAL);

        // 결과가 PENDING(decidedAt null).
        StageResult pending = StageResult.initialize(announced, application);
        assertThat(service.evaluate(application, posting, policy(RetentionBaselineType.HIRING_ENDED_AT),
                false, List.of(announced), pending, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.APPLICATION_NOT_TERMINAL);
    }
}
