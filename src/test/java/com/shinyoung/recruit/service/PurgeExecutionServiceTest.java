package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.PurgeExecuteRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.dto.response.PurgeJobItemResponse;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.PurgeBatchStatus;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Purge execute 통합 검증(Phase 09d-1). item 트랜잭션이 REQUIRES_NEW 라 테스트 트랜잭션으로 감싸면
 * fixture 가 보이지 않으므로 <b>비-트랜잭션 + JdbcTemplate 정리</b>로 실행한다(commit 의미론 실검증).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class PurgeExecutionServiceTest {

    @Autowired private PurgeExecutionService purgeExecutionService;
    @Autowired private RetentionDryRunService retentionDryRunService;
    @Autowired private RetentionPolicyService retentionPolicyService;
    @Autowired private RetentionAnchorService retentionAnchorService;
    @Autowired private RetentionHoldService retentionHoldService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private StageService stageService;
    @Autowired private StageResultService stageResultService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationAttachmentRepository attachmentRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private PurgeBatchRepository purgeBatchRepository;
    @Autowired private AuditHmac auditHmac;
    @Autowired private AttachmentProperties attachmentProperties;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() throws Exception {
        // 테스트 실패 시 잔존할 수 있는 실파일 정리(정상 흐름에선 saga 가 삭제한다).
        Files.deleteIfExists(realBinaryPath());
        // 비-트랜잭션 테스트가 커밋한 행 전부 정리(FK 자식 → 부모 순). 다른 테스트와의 공유 DB 오염 방지.
        List<String> tables = List.of(
                "activity_log", "purge_job_item", "purge_batch", "retention_hold", "retention_policy",
                "interview_evaluation", "interview_participant", "interview",
                "stage_result_correction_history", "stage_result", "stage",
                "application_attachment", "application_answer",
                "application_education_semester_grade", "application_education",
                "application_career_profile", "application_career", "application_certificate",
                "application_language", "application_military", "application_award", "application_gap_period",
                "job_application", "job_posting_question", "job_posting_attachment_requirement",
                "application_form_config", "job_position", "job_posting",
                "applicant", "employee", "users");
        for (String table : tables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    void execute는_재검증_후_tombstone하고_saga로_바이너리_소멸_확인까지_완주한다() throws Exception {
        // ---- fixture: 최종전형 확정 + anchor 60일 전 + 전역 정책 30일 ----
        Long jobPostingId = createPosting();
        Long finalStageId = stageService.create(jobPostingId, new StageCreateRequest(
                "final", StageType.DOCUMENT, 0, LocalDateTime.now().plusDays(40), true));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, finalStageId);

        Long appNoBinary = createSubmittedApplication("purge-exec-a", jobPostingId);
        Long appWithBinary = createSubmittedApplication("purge-exec-b", jobPostingId);
        Long appDriftHold = createSubmittedApplication("purge-exec-c", jobPostingId);
        Long appSoftDeletedBinary = createSubmittedApplication("purge-exec-d", jobPostingId);
        Long appBinaryDeleteFails = createSubmittedApplication("purge-exec-e", jobPostingId);

        stageResultService.initialize(finalStageId);
        for (AdminStageResultResponse result : stageResultService.getResults(finalStageId)) {
            stageResultService.updateResult(finalStageId, result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.FAILED, null, null), "employee01");
        }
        stageService.announce(jobPostingId, finalStageId, "employee01");

        // B = STORED 바이너리 첨부 + storage root 에 **실파일** 생성 — 핵심 경로(실삭제 + exists 재확인 +
        // BINARY_DELETED) 실증(9d-2 리뷰 Medium 2 — MISSING_AS_SUCCESS 만으로는 불충분).
        JobApplication appB = jobApplicationRepository.findById(appWithBinary).orElseThrow();
        attachmentRepository.save(ApplicationAttachment.createStored(
                appB, AttachmentType.PORTFOLIO, ApplicationSectionType.ATTACHMENT, null,
                "홍길동_포트폴리오.pdf", "stored-random.pdf", "attachments/stored-random.pdf",
                "application/pdf", 1024L, 1));
        Path realBinary = realBinaryPath();
        Files.createDirectories(realBinary.getParent());
        Files.write(realBinary, "dummy-pdf-binary".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.exists(realBinary)).isTrue();

        // D = soft-delete 첨부 — 물리삭제 실패로 파일이 잔존할 수 있는 상태(소멸 미확인 → saga 대상).
        JobApplication appD = jobApplicationRepository.findById(appSoftDeletedBinary).orElseThrow();
        ApplicationAttachment softDeleted = ApplicationAttachment.createStored(
                appD, AttachmentType.RESUME, ApplicationSectionType.ATTACHMENT, null,
                "홍길동_이력서.pdf", "stored-random2.pdf", "attachments/stored-random2.pdf",
                "application/pdf", 2048L, 1);
        softDeleted.markDeleted("admin01",
                com.shinyoung.recruit.enumeration.AttachmentDeleteActorType.EMPLOYEE,
                "soft delete", LocalDateTime.now());
        attachmentRepository.save(softDeleted);

        // E = 물리 삭제가 실패하는 첨부(storage root 밖 traversal 경로 → invalidPath 거부) — saga 실패 경로.
        JobApplication appE = jobApplicationRepository.findById(appBinaryDeleteFails).orElseThrow();
        attachmentRepository.save(ApplicationAttachment.createStored(
                appE, AttachmentType.PORTFOLIO, ApplicationSectionType.ATTACHMENT, null,
                "홍길동_증빙.pdf", "stored-random3.pdf", "../escape.pdf",
                "application/pdf", 512L, 1));

        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(60)), "privacy01");

        // ---- dry-run(전부 ELIGIBLE) → 그 후 C 에 hold → execute 재검증 drift ----
        PurgeBatchDetailResponse dryRun = retentionDryRunService.dryRun("recruit-admin01");
        assertThat(dryRun.batch().eligibleCount()).isEqualTo(5);
        retentionHoldService.set(new RetentionHoldCreateRequest(appDriftHold, "소송 보존"), "privacy01");

        PurgeBatchDetailResponse executed = purgeExecutionService.execute(
                new PurgeExecuteRequest(true, dryRun.batch().id(), null), "privacy01");

        // ---- batch/item 집계: saga 성공(B/D)은 최종 PURGED, saga 실패(E)는 PENDING 유지 + PARTIAL_FAILED ----
        assertThat(executed.batch().status()).isEqualTo(PurgeBatchStatus.PARTIAL_FAILED);
        assertThat(executed.batch().sourceDryRunBatchId()).isEqualTo(dryRun.batch().id());
        assertThat(executed.batch().totalCount()).isEqualTo(5);
        assertThat(executed.batch().purgedCount()).isEqualTo(3);
        assertThat(executed.batch().pendingCount()).isEqualTo(1);
        assertThat(executed.batch().skippedCount()).isEqualTo(1);
        assertThat(executed.batch().failedCount()).isZero();
        assertThat(executed.batch().binaryDeleteFailedCount()).isEqualTo(1);

        Map<Long, PurgeJobItemResponse> items = executed.items().stream()
                .collect(Collectors.toMap(PurgeJobItemResponse::applicationId, Function.identity()));
        assertThat(items.get(appNoBinary).status()).isEqualTo(PurgeItemStatus.PURGED);
        // saga 완주: B = 실파일 삭제 + exists 재확인, D = 파일 부재(MISSING_AS_SUCCESS) → PENDING→PURGED 승격.
        assertThat(items.get(appWithBinary).status()).isEqualTo(PurgeItemStatus.PURGED);
        assertThat(items.get(appSoftDeletedBinary).status()).isEqualTo(PurgeItemStatus.PURGED);
        // saga 실패(E): PENDING 유지 — reconciliation(9e) 대상.
        assertThat(items.get(appBinaryDeleteFails).status()).isEqualTo(PurgeItemStatus.PENDING);
        assertThat(items.get(appDriftHold).status()).isEqualTo(PurgeItemStatus.SKIPPED);
        assertThat(items.get(appDriftHold).reasonCode()).isEqualTo(AuditReasonCode.RETENTION_HOLD);

        // ---- 첨부 saga 결과: 성공 = BINARY_DELETED(storagePath null·binaryDeletedAt·PII 제거), 실패 = FAILED 유지 ----
        // 실파일이 물리적으로 소멸했는지 실증(리뷰 Medium 2).
        assertThat(Files.exists(realBinary)).isFalse();

        ApplicationAttachment purgedAttachment = attachmentRepository
                .findByJobApplicationId(appWithBinary).get(0);
        assertThat(purgedAttachment.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.BINARY_DELETED);
        assertThat(purgedAttachment.getStoragePath()).isNull();
        assertThat(purgedAttachment.getBinaryDeletedAt()).isNotNull();
        assertThat(purgedAttachment.getOriginalFileName()).isEqualTo(JobApplication.PURGED_PLACEHOLDER);
        assertThat(purgedAttachment.getFilenameHash())
                .isEqualTo(auditHmac.hmacHex("FILE_NAME:홍길동_포트폴리오.pdf"));
        assertThat(purgedAttachment.getDeletionReason()).isNull();

        ApplicationAttachment failedAttachment = attachmentRepository
                .findByJobApplicationId(appBinaryDeleteFails).get(0);
        assertThat(failedAttachment.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.BINARY_DELETE_FAILED);
        assertThat(failedAttachment.getStoragePath()).isEqualTo("../escape.pdf"); // 재시도 위해 보존
        assertThat(failedAttachment.getOriginalFileName()).isEqualTo(JobApplication.PURGED_PLACEHOLDER); // PII 는 제거됨

        // ---- marker: saga 완주(B) = 최종 PURGED + purgedAt, saga 실패(E) = PURGE_PENDING(purgedAt 미세팅) ----
        JobApplication purgedApp = jobApplicationRepository.findById(appNoBinary).orElseThrow();
        assertThat(purgedApp.getPurgeResult()).isEqualTo(PurgeResult.PURGED);
        assertThat(purgedApp.getPurgedAt()).isNotNull();
        assertThat(purgedApp.getApplicantNameSnapshot()).isEqualTo(JobApplication.PURGED_PLACEHOLDER);

        JobApplication sagaPromotedApp = jobApplicationRepository.findById(appWithBinary).orElseThrow();
        assertThat(sagaPromotedApp.getPurgeResult()).isEqualTo(PurgeResult.PURGED);
        assertThat(sagaPromotedApp.getPurgedAt()).isNotNull();

        JobApplication sagaFailedApp = jobApplicationRepository.findById(appBinaryDeleteFails).orElseThrow();
        assertThat(sagaFailedApp.getPurgeResult()).isEqualTo(PurgeResult.PURGE_PENDING);
        assertThat(sagaFailedApp.getPurgedAt()).isNull();
        assertThat(sagaFailedApp.getApplicantNameSnapshot()).isEqualTo(JobApplication.PURGED_PLACEHOLDER);

        JobApplication heldApp = jobApplicationRepository.findById(appDriftHold).orElseThrow();
        assertThat(heldApp.getPurgeResult()).isNull(); // SKIP — 무변경
        assertThat(heldApp.getApplicantNameSnapshot()).isNotEqualTo(JobApplication.PURGED_PLACEHOLDER);

        // ---- Applicant ref-count 익명화(파기 대상 marker 가 전부일 때만) ----
        Applicant purgedApplicant = applicantRepository.findById(purgedApp.getApplicant().getId()).orElseThrow();
        assertThat(purgedApplicant.getLoginId()).isNull();
        assertThat(purgedApplicant.getEmail()).isNull();
        assertThat(purgedApplicant.getPhoneNumber()).isNull();
        assertThat(purgedApplicant.getCi()).isNull();
        assertThat(purgedApplicant.getCiHash()).startsWith("PURGED:");

        Applicant sagaFailedApplicant = applicantRepository.findById(sagaFailedApp.getApplicant().getId()).orElseThrow();
        assertThat(sagaFailedApplicant.getCiHash()).startsWith("PURGED:"); // PENDING 도 파기 대상 — ref0 충족

        Applicant heldApplicant = applicantRepository.findById(heldApp.getApplicant().getId()).orElseThrow();
        assertThat(heldApplicant.getLoginId()).isEqualTo("purge-exec-c"); // 보존

        // ---- coarse 감사(PURGE_EXECUTE) ----
        List<ActivityLog> audits = activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, AuditActionType.PURGE_EXECUTE, null, null, null, null, PageRequest.of(0, 10)).getContent();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActorId()).isEqualTo("privacy01");
        assertThat(audits.get(0).getMetadataJson())
                .contains("\"purgedCount\":3").contains("\"pendingCount\":1")
                .contains("\"skippedCount\":1").contains("\"binaryDeleteFailedCount\":1");

        // ---- 멱등: 같은 dry-run 으로 재실행 → 전부 ALREADY_PURGED SKIP ----
        PurgeBatchDetailResponse second = purgeExecutionService.execute(
                new PurgeExecuteRequest(true, dryRun.batch().id(), null), "privacy01");
        assertThat(second.batch().purgedCount()).isZero();
        assertThat(second.items().stream()
                .filter(item -> List.of(appNoBinary, appWithBinary).contains(item.applicationId()))
                .map(PurgeJobItemResponse::reasonCode))
                .containsOnly(AuditReasonCode.ALREADY_PURGED);
    }

    @Test
    void confirm_없거나_모드_모호하면_400_단건은_재검증_후_실행된다() {
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(false, 1L, null), "privacy01"))
                .isInstanceOf(InvalidRetentionRequestException.class);
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(true, 1L, 1L), "privacy01"))
                .isInstanceOf(InvalidRetentionRequestException.class);
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(true, null, null), "privacy01"))
                .isInstanceOf(InvalidRetentionRequestException.class);
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(true, 1L, null), " "))
                .isInstanceOf(InvalidRetentionRequestException.class);

        // source dry-run 상태 검증(9d-1 리뷰 Medium 2) — RUNNING DRY_RUN 거부.
        LocalDateTime now = LocalDateTime.now();
        PurgeBatch runningDryRun = purgeBatchRepository.save(PurgeBatch.startDryRun(now, now, "recruit-admin01"));
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(true, runningDryRun.getId(), null), "privacy01"))
                .isInstanceOf(InvalidRetentionRequestException.class)
                .hasMessageContaining("COMPLETED DRY_RUN");

        // COMPLETED 라도 EXECUTE batch 는 근거가 될 수 없다.
        PurgeBatch completedExecute = PurgeBatch.startExecute(now, now, "privacy01", null);
        completedExecute.completeExecute(0, 0, 0, 0, 0, 0, now);
        PurgeBatch savedExecute = purgeBatchRepository.save(completedExecute);
        assertThatThrownBy(() -> purgeExecutionService.execute(
                new PurgeExecuteRequest(true, savedExecute.getId(), null), "privacy01"))
                .isInstanceOf(InvalidRetentionRequestException.class)
                .hasMessageContaining("COMPLETED DRY_RUN");

        // 단건(applicationId) — 적격이면 batch 1건으로 실행된다.
        Long jobPostingId = createPosting();
        jobPostingService.publish(jobPostingId);
        Long applicationId = createSubmittedApplication("purge-exec-single", jobPostingId);
        JobApplication single = jobApplicationRepository.findById(applicationId).orElseThrow();
        single.withdraw(LocalDateTime.now()); // WITHDRAWN = terminal(검증 우회 — 비-tx 라 detached 수정 후 save)
        jobApplicationRepository.save(single);

        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(60)), "privacy01");

        PurgeBatchDetailResponse result = purgeExecutionService.execute(
                new PurgeExecuteRequest(true, null, applicationId), "privacy01");

        assertThat(result.batch().totalCount()).isEqualTo(1);
        assertThat(result.batch().purgedCount()).isEqualTo(1);
        assertThat(result.batch().sourceDryRunBatchId()).isNull();
    }

    private Path realBinaryPath() {
        return attachmentProperties.getStorageRoot().toAbsolutePath().normalize()
                .resolve("attachments/stored-random.pdf");
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "purge execute recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
    }

    private Long createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = new Applicant(loginId + "-ci", HashUtil.sha256(loginId + "-ci"));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long jobPositionId = jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId).findFirst().orElseThrow();
        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionId));
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }
}
