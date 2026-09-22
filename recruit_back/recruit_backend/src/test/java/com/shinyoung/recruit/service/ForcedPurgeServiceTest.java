package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ForcedPurgeRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.enumeration.ForcedPurgeReason;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.support.JobPostingImageTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 강제 파기 오케스트레이션 통합 검증(Phase 10). item 트랜잭션이 REQUIRES_NEW 라 테스트 트랜잭션으로 감싸면
 * fixture 가 보이지 않으므로 <b>비-트랜잭션 + JdbcTemplate 정리</b>로 실행한다(commit 의미론 실검증,
 * {@link PurgeExecutionServiceTest} 와 동일 방식).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class ForcedPurgeServiceTest {

    @Autowired private ForcedPurgeService forcedPurgeService;
    @Autowired private RetentionPolicyService retentionPolicyService;
    @Autowired private RetentionAnchorService retentionAnchorService;
    @Autowired private RetentionHoldService retentionHoldService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingImageRepository jobPostingImageRepository;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private PurgeBatchRepository purgeBatchRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    @DisplayName("confirm 이 true 가 아니면 400")
    void requiresConfirm() {
        Applicant applicant = createApplicant("forced-confirm");

        assertThatThrownBy(() -> forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DATA_SUBJECT_REQUEST, false), "tester"))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    @Test
    @DisplayName("active hold 가 하나라도 있으면 400 으로 거부한다")
    void rejectsWhenHoldExists() {
        // given: 그 지원자의 지원서 하나에 해제되지 않은 RetentionHold
        Long jobPostingId = createPosting();
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId);
        jobPostingService.publish(jobPostingId);
        Applicant applicant = createApplicant("forced-hold");
        Long applicationId = createSubmittedApplicationForApplicant(applicant, jobPostingId);
        retentionHoldService.set(new RetentionHoldCreateRequest(applicationId, "소송 보존"), "privacy01");

        assertThatThrownBy(() -> forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester"))
                .isInstanceOf(InvalidRetentionRequestException.class);

        // then: PurgeBatch 가 생성되지 않았는지도 확인 — 보류가 있으면 원장에 흔적을 남기지 않는다.
        assertThat(purgeBatchRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("보존기간이 남아 있어도 지원자의 모든 지원서를 파기하고 계정을 익명화한다")
    void purgesAllApplicationsAndAnonymizesAccount() {
        // given: 같은 지원자의 지원서 2건, 보존기간 미도래 상태(정책 존재 + anchor 확정 + 미도래)
        Long jobPostingId1 = createPosting();
        Long jobPostingId2 = createPosting();
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId1);
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId2);
        jobPostingService.publish(jobPostingId1);
        jobPostingService.publish(jobPostingId2);

        Applicant applicant = createApplicant("forced-all");
        Long app1 = createSubmittedApplicationForApplicant(applicant, jobPostingId1);
        Long app2 = createSubmittedApplicationForApplicant(applicant, jobPostingId2);

        // 보존기간 3650일 + anchor 1일 전 = 아직 한참 남음. forced 는 이를 무시하고 파기해야 한다.
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 3650, RetentionBaselineType.HIRING_ENDED_AT, true, null, null), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId1,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(1)), "privacy01");
        retentionAnchorService.fixAnchor(jobPostingId2,
                new RetentionAnchorRequest(LocalDateTime.now().minusDays(1)), "privacy01");

        PurgeBatchDetailResponse response = forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester");

        // then: 파기 2건, Applicant 의 userName·email·loginId 가 null, ciHash 가 "PURGED:" 로 시작
        assertThat(response.batch().totalCount()).isEqualTo(2);
        assertThat(response.batch().purgedCount()).isEqualTo(2);
        assertThat(response.batch().pendingCount()).isZero();
        assertThat(response.batch().skippedCount()).isZero();
        assertThat(response.batch().failedCount()).isZero();

        Applicant purged = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(purged.getUserName()).isNull();
        assertThat(purged.getEmail()).isNull();
        assertThat(purged.getLoginId()).isNull();
        assertThat(purged.getCiHash()).startsWith("PURGED:");

        assertThat(jobApplicationRepository.findById(app1).orElseThrow().getPurgeResult()).isNotNull();
        assertThat(jobApplicationRepository.findById(app2).orElseThrow().getPurgeResult()).isNotNull();
    }

    @Test
    @DisplayName("지원 이력이 0건인 계정도 익명화한다")
    void anonymizesAccountWithoutApplications() {
        Applicant applicant = createApplicant("forced-none");

        PurgeBatchDetailResponse response = forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DUPLICATE_ACCOUNT, true), "tester");

        assertThat(response.batch().totalCount()).isZero();
        Applicant purged = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(purged.getCiHash()).startsWith("PURGED:");
    }

    @Test
    @DisplayName("이미 파기된 지원자를 다시 요청하면 전부 SKIPPED 로 끝난다")
    void secondRequestSkips() {
        Long jobPostingId1 = createPosting();
        Long jobPostingId2 = createPosting();
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId1);
        JobPostingImageTestSupport.attachContentImage(jobPostingRepository, jobPostingImageRepository, jobPostingId2);
        jobPostingService.publish(jobPostingId1);
        jobPostingService.publish(jobPostingId2);

        Applicant applicant = createApplicant("forced-twice");
        createSubmittedApplicationForApplicant(applicant, jobPostingId1);
        createSubmittedApplicationForApplicant(applicant, jobPostingId2);

        // 1차 파기
        forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester");

        // 2차 요청 → 전부 ALREADY_PURGED SKIP
        PurgeBatchDetailResponse second = forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.DATA_SUBJECT_REQUEST, true), "tester");

        assertThat(second.batch().skippedCount()).isEqualTo(2);
        assertThat(second.batch().purgedCount()).isZero();
    }

    @Test
    @DisplayName("없는 지원자는 404")
    void notFound() {
        assertThatThrownBy(() -> forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(999_999L, ForcedPurgeReason.OTHER, true), "tester"))
                .isInstanceOf(ApplicantNotFoundException.class);
    }

    @Test
    @DisplayName("actor 가 비면 거부한다(ANONYMOUS 감사 차단)")
    void requiresActor() {
        Applicant applicant = createApplicant("forced-actor");

        assertThatThrownBy(() -> forcedPurgeService.forcePurge(
                new ForcedPurgeRequest(applicant.getId(), ForcedPurgeReason.OTHER, true), "  "))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "forced purge recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
    }

    private Applicant createApplicant(String loginId) {
        Applicant applicant = new Applicant(HashUtil.sha256(loginId + "-ci"));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createSubmittedApplicationForApplicant(Applicant applicant, Long jobPostingId) {
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
