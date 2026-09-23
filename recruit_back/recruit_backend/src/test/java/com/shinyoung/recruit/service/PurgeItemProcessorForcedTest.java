package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.RetentionHold;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.PurgeItemStatus;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.support.JobPostingImageTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 강제 파기(Phase 10) eligibility 판정 검증 — forced=true 면 ALREADY_PURGED·RETENTION_HOLD 두 가지만
 * 남기고 보존기간·anchor·전형 종료 여부는 무시한다. item 트랜잭션이 REQUIRES_NEW 라 테스트 트랜잭션으로
 * 감싸면 fixture 가 보이지 않으므로 {@link PurgeExecutionServiceTest} 와 동일하게
 * <b>비-트랜잭션 + JdbcTemplate 정리</b>로 실행한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class PurgeItemProcessorForcedTest {

    @Autowired private PurgeItemProcessor purgeItemProcessor;
    @Autowired private RetentionPolicyService retentionPolicyService;
    @Autowired private RetentionAnchorService retentionAnchorService;
    @Autowired private StageService stageService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingImageRepository jobPostingImageRepository;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private RetentionHoldRepository retentionHoldRepository;
    @Autowired private PurgeBatchRepository purgeBatchRepository;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbcTemplate;
    // 커밋된 제출이 비동기 제출 완료 메일(message_recipient → job_application FK)을 만들지 않게 막는다.
    @MockitoBean private ApplicationSubmittedMailListener applicationSubmittedMailListener;

    @AfterEach
    void cleanUp() {
        // 비-트랜잭션 테스트가 커밋한 행 전부 정리(FK 자식 → 부모 순). 다른 테스트와의 공유 DB 오염 방지.
        List<String> tables = List.of(
                "activity_log", "purge_job_item", "purge_batch", "retention_hold", "retention_policy",
                "interview_evaluation", "interview_participant", "interview",
                "stage_result_correction_history", "stage_result", "stage",
                "application_attachment", "application_answer", "application_basic_info",
                "application_education_semester_grade", "application_education",
                "application_career", "application_certificate",
                "application_language", "application_military", "application_award", "application_gap_period",
                "job_application", "job_posting_question", "job_posting_attachment_requirement",
                "application_form_config", "job_position", "job_posting_image", "job_posting",
                "applicant", "employee", "users");
        for (String table : tables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("forced=true 면 보존기간이 남아 있어도 파기한다")
    void forcedIgnoresRetentionNotDue() {
        // given: 방금 마감된 공고(closedAt = now)의 제출 지원서 — 일반 경로면 RETENTION_NOT_DUE
        Long jobPostingId = publishPosting(createPosting());
        Long applicationId = createSubmittedApplication("forced-not-due", jobPostingId);
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId,
                new RetentionAnchorRequest(LocalDateTime.now(clock)), "privacy01");
        Long batchId = createBatchId();

        PurgeItemProcessor.PurgeItemOutcome outcome =
                purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

        assertThat(outcome.status()).isIn(PurgeItemStatus.PURGED, PurgeItemStatus.PENDING);
        assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getPurgeResult()).isNotNull();
    }

    @Test
    @DisplayName("forced=true 여도 active hold 가 있으면 SKIPPED(RETENTION_HOLD)")
    void forcedRespectsHold() {
        // given: 해당 지원서에 해제되지 않은 RetentionHold 저장
        Long jobPostingId = publishPosting(createPosting());
        Long applicationId = createSubmittedApplication("forced-hold", jobPostingId);
        retentionHoldRepository.save(RetentionHold.create(applicationId, "정보주체 확인 필요", "privacy01"));
        Long batchId = createBatchId();

        PurgeItemProcessor.PurgeItemOutcome outcome =
                purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

        assertThat(outcome.status()).isEqualTo(PurgeItemStatus.SKIPPED);
        assertThat(outcome.reasonCode()).isEqualTo(AuditReasonCode.RETENTION_HOLD);
    }

    @Test
    @DisplayName("forced=true 여도 이미 파기된 건은 SKIPPED(ALREADY_PURGED)")
    void forcedRespectsAlreadyPurged() {
        Long jobPostingId = publishPosting(createPosting());
        Long applicationId = createSubmittedApplication("forced-already", jobPostingId);
        Long batchId = createBatchId();

        purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

        PurgeItemProcessor.PurgeItemOutcome second =
                purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

        assertThat(second.status()).isEqualTo(PurgeItemStatus.SKIPPED);
        assertThat(second.reasonCode()).isEqualTo(AuditReasonCode.ALREADY_PURGED);
    }

    @Test
    @DisplayName("forced=true 는 전형이 종료되지 않은 진행 중 지원서도 파기한다")
    void forcedIgnoresNotTerminal() {
        // given: 정책·anchor 로 보존기간은 이미 도래시키되 finalStage 는 READY(미종료)로 남겨
        // 일반 경로가 ①~⑥ 을 전부 통과하고 정확히 ⑦ APPLICATION_NOT_TERMINAL 에서만 걸리도록 격리한다.
        Long jobPostingId = createPosting();
        stageService.create(jobPostingId, new StageCreateRequest(
                "final", StageType.DOCUMENT, 0, LocalDateTime.now().plusDays(40), true));
        publishPosting(jobPostingId);
        Long applicationId = createSubmittedApplication("forced-not-terminal", jobPostingId);

        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId,
                new RetentionAnchorRequest(LocalDateTime.now(clock).minusDays(60)), "privacy01");
        Long batchId = createBatchId();

        // 전제 고정: 일반 경로(forced=false)는 정책·보존기간을 통과하고 전형 미종료에서만 SKIP 된다.
        PurgeItemProcessor.PurgeItemOutcome normal =
                purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), false);
        assertThat(normal.status()).isEqualTo(PurgeItemStatus.SKIPPED);
        assertThat(normal.reasonCode()).isEqualTo(AuditReasonCode.APPLICATION_NOT_TERMINAL);

        PurgeItemProcessor.PurgeItemOutcome outcome =
                purgeItemProcessor.process(batchId, applicationId, LocalDateTime.now(clock), true);

        assertThat(outcome.status()).isIn(PurgeItemStatus.PURGED, PurgeItemStatus.PENDING);
        assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getPurgeResult()).isNotNull();
    }

    private Long createBatchId() {
        LocalDateTime now = LocalDateTime.now(clock);
        return purgeBatchRepository.save(PurgeBatch.startExecute(now, now, "privacy01", null)).getId();
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "forced purge recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
    }

    private Long publishPosting(Long jobPostingId) {
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId);
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = new Applicant(HashUtil.sha256(loginId + "-ci"));
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
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }
}
