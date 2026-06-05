package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeJobItemResponse;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PurgeBatchMode;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 09c dry-run 통합 검증 — eligibility 산정이 PurgeBatch/PurgeJobItem 으로만 기록되고(무변경),
 * batch 단위 coarse 감사(PURGE_SCAN)가 in-tx 로 남는지 실증한다. 접수기간은 동적(날짜 의존 사전-실패 회피).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class RetentionDryRunServiceTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private RetentionPolicyService retentionPolicyService;

    @Autowired
    private RetentionHoldService retentionHoldService;

    @Autowired
    private RetentionAnchorService retentionAnchorService;

    @Autowired
    private RetentionDryRunService retentionDryRunService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Test
    void dry_run은_eligibility를_산정해_batch와_item으로만_기록한다() {
        // posting1: anchor 확정(60일 전) + 전역 정책(30일) → due. 최종전형 확정.
        Long posting1 = createPosting();
        Long finalStageId = stageService.create(posting1, new StageCreateRequest(
                "final", StageType.DOCUMENT, 0, LocalDateTime.now().plusDays(40), true));
        jobPostingService.publish(posting1);
        stageService.start(posting1, finalStageId);

        Long eligibleAppId = createSubmittedApplication("dryrun-eligible", posting1);
        Long withdrawnAppId = createSubmittedApplication("dryrun-withdrawn", posting1);
        Long heldAppId = createSubmittedApplication("dryrun-held", posting1);

        stageResultService.initialize(finalStageId);
        for (AdminStageResultResponse result : stageResultService.getResults(finalStageId)) {
            stageResultService.updateResult(finalStageId, result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.PASSED, null, null), "employee01");
        }
        stageService.announce(posting1, finalStageId, "employee01");

        JobApplication withdrawn = jobApplicationRepository.findById(withdrawnAppId).orElseThrow();
        withdrawn.withdraw(LocalDateTime.now());

        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(posting1,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(60)), "privacy01");
        retentionHoldService.set(new RetentionHoldCreateRequest(heldAppId, "법적 분쟁 보존"), "privacy01");

        // posting2: anchor 미확정 → ANCHOR_NOT_FIXED.
        Long posting2 = createPosting();
        jobPostingService.publish(posting2);
        Long anchorlessAppId = createSubmittedApplication("dryrun-anchorless", posting2);

        PurgeBatchDetailResponse response = retentionDryRunService.dryRun("recruit-admin01");

        assertThat(response.batch().mode()).isEqualTo(PurgeBatchMode.DRY_RUN);
        assertThat(response.batch().status()).isEqualTo(PurgeBatchStatus.COMPLETED);
        assertThat(response.batch().totalCount()).isEqualTo(4);
        assertThat(response.batch().eligibleCount()).isEqualTo(2);
        assertThat(response.batch().skippedCount()).isEqualTo(2);
        assertThat(response.batch().policyConflictCount()).isZero();

        Map<Long, PurgeJobItemResponse> byApplication = response.items().stream()
                .collect(Collectors.toMap(PurgeJobItemResponse::applicationId, Function.identity()));
        assertThat(byApplication.get(eligibleAppId).status()).isEqualTo(PurgeItemStatus.ELIGIBLE);
        assertThat(byApplication.get(withdrawnAppId).status()).isEqualTo(PurgeItemStatus.ELIGIBLE);
        assertThat(byApplication.get(heldAppId).status()).isEqualTo(PurgeItemStatus.SKIPPED);
        assertThat(byApplication.get(heldAppId).reasonCode()).isEqualTo(AuditReasonCode.RETENTION_HOLD);
        assertThat(byApplication.get(anchorlessAppId).reasonCode()).isEqualTo(AuditReasonCode.ANCHOR_NOT_FIXED);

        // 무변경(preview) — 도메인 marker 미세팅.
        assertThat(jobApplicationRepository.findById(eligibleAppId).orElseThrow().getPurgeResult()).isNull();

        // batch 단위 coarse 감사(PURGE_SCAN, in-tx) — 집계 metadata 포함.
        List<ActivityLog> scans = activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, AuditActionType.PURGE_SCAN, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
        assertThat(scans).hasSize(1);
        assertThat(scans.get(0).getActorId()).isEqualTo("recruit-admin01");
        assertThat(scans.get(0).getTargetId()).isEqualTo(String.valueOf(response.batch().id()));
        assertThat(scans.get(0).getMetadataJson())
                .contains("\"eligibleCount\":2")
                .contains("\"policyConflictCount\":0");
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "retention dry-run recruitment",
                "<p>content</p>",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private Long createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = createApplicant(loginId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }

    private Applicant createApplicant(String loginId) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long firstJobPositionId(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .findFirst()
                .orElseThrow();
    }
}
