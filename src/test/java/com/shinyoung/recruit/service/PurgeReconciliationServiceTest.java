package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.PurgeReconcileResponse;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Purge reconciliation sweep 통합 검증(Phase 09e). saga 가 application 별 REQUIRES_NEW 라
 * 비-트랜잭션 + JdbcTemplate 정리로 실행한다(9d-1/9d-2 패턴 동일).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class PurgeReconciliationServiceTest {

    @Autowired private PurgeReconciliationService purgeReconciliationService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationAttachmentRepository attachmentRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private PurgeBatchRepository purgeBatchRepository;
    @Autowired private PurgeJobItemRepository purgeJobItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<String> tables = List.of(
                "activity_log", "purge_job_item", "purge_batch",
                "application_attachment", "job_application",
                "application_form_config", "job_position", "job_posting",
                "applicant", "employee", "users");
        for (String table : tables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    void reconcile는_PURGE_PENDING_잔여건의_바이너리를_삭제하고_최종_PURGED로_승격한다() {
        Long jobPostingId = createPosting();
        jobPostingService.publish(jobPostingId);

        // 9d-2 saga 가 바이너리 삭제에 실패해 PURGE_PENDING + BINARY_DELETE_FAILED 로 멈춘 상태를 재현.
        Long applicationId = createPurgePendingWithFailedAttachment("recon-a", jobPostingId, "attachments/recon-a.pdf");

        PurgeReconcileResponse response = purgeReconciliationService.reconcile("privacy01");

        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.promotedCount()).isEqualTo(1);
        assertThat(response.stillPendingCount()).isZero();
        assertThat(response.errorCount()).isZero();

        JobApplication promoted = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(promoted.getPurgeResult()).isEqualTo(PurgeResult.PURGED);
        assertThat(promoted.getPurgedAt()).isNotNull();

        ApplicationAttachment attachment = attachmentRepository.findByJobApplicationId(applicationId).get(0);
        assertThat(attachment.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.BINARY_DELETED);
        assertThat(attachment.getStoragePath()).isNull();
        assertThat(attachment.getBinaryDeletedAt()).isNotNull();
        assertThat(attachment.getBinaryDeleteFailureCode()).isNull(); // 성공 시 실패코드 해소

        List<ActivityLog> audits = activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, AuditActionType.PURGE_RECONCILE, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionResult()).isEqualTo(AuditActionResult.SUCCESS);
        assertThat(audits.get(0).getActorId()).isEqualTo("privacy01");
        assertThat(audits.get(0).getMetadataJson())
                .contains("\"scannedCount\":1").contains("\"promotedCount\":1");

        // 멱등: 더는 PURGE_PENDING 이 없으므로 두 번째 sweep 은 무대상.
        PurgeReconcileResponse second = purgeReconciliationService.reconcile("privacy01");
        assertThat(second.scannedCount()).isZero();
        assertThat(second.promotedCount()).isZero();
    }

    @Test
    void reconcile는_빈_storagePath_등_재처리_불가건은_PENDING으로_유지하고_FAILURE감사를_남긴다() {
        Long jobPostingId = createPosting();
        jobPostingService.publish(jobPostingId);

        // storagePath 가 비어 있어 소멸 확인이 불가능한 BINARY_DELETE_FAILED — 재처리해도 승격 금지(Major 1).
        Long applicationId = createPurgePendingWithFailedAttachment("recon-b", jobPostingId, "");

        PurgeReconcileResponse response = purgeReconciliationService.reconcile("privacy01");

        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.promotedCount()).isZero();
        assertThat(response.stillPendingCount()).isEqualTo(1);

        JobApplication stillPending = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(stillPending.getPurgeResult()).isEqualTo(PurgeResult.PURGE_PENDING);
        assertThat(stillPending.getPurgedAt()).isNull();

        ApplicationAttachment attachment = attachmentRepository.findByJobApplicationId(applicationId).get(0);
        assertThat(attachment.getPhysicalFileStatus()).isEqualTo(PhysicalFileStatus.BINARY_DELETE_FAILED);
        assertThat(attachment.getBinaryDeleteFailureCode()).isEqualTo("EMPTY_STORAGE_PATH");

        List<ActivityLog> audits = activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, AuditActionType.PURGE_RECONCILE, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getActionResult()).isEqualTo(AuditActionResult.FAILURE);
    }

    @Test
    void reconcile는_actor가_없으면_거부한다() {
        assertThatThrownBy(() -> purgeReconciliationService.reconcile(" "))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    /** 관계형 PII 제거는 끝났으나 바이너리 삭제만 실패한 상태(PURGE_PENDING + BINARY_DELETE_FAILED)를 만든다. */
    private Long createPurgePendingWithFailedAttachment(String loginId, Long jobPostingId, String storagePath) {
        Applicant applicant = new Applicant(loginId + "-ci", HashUtil.sha256(loginId + "-ci"));
        applicant.setLoginId(loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded");
        applicant = applicantRepository.save(applicant);

        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long jobPositionId = jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId).findFirst().orElseThrow();
        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionId));

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        ApplicationAttachment attachment = ApplicationAttachment.createStored(
                application, AttachmentType.PORTFOLIO, ApplicationSectionType.ATTACHMENT, null,
                JobApplication.PURGED_PLACEHOLDER, "stored.pdf",
                storagePath.isEmpty() ? "attachments/placeholder.pdf" : storagePath,
                "application/pdf", 512L, 1);
        attachment.markBinaryDeleteFailed("PRIOR_FAILURE");
        ReflectionTestUtils.setField(attachment, "storagePath", storagePath.isEmpty() ? null : storagePath);
        attachmentRepository.save(attachment);

        // 원 execute 가 남긴 PURGE_PENDING ledger(batch + PENDING item)를 엔티티 팩토리로 재현.
        LocalDateTime now = LocalDateTime.now();
        PurgeBatch batch = purgeBatchRepository.save(PurgeBatch.startExecute(now, now, "privacy01", null));
        purgeJobItemRepository.save(PurgeJobItem.executePending(batch, applicationId, jobPostingId));

        application.markPurgePending(batch.getId());
        jobApplicationRepository.save(application);
        return applicationId;
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "reconcile recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
    }
}
