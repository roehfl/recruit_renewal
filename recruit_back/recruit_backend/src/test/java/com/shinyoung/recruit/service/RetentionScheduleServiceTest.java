package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.response.RetentionScheduleResponse;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다음 파기 예정일 계산 통합 검증(Phase 10, Task 8) — 스케줄 게이트②(전량 스캔 방지)의 근거가 되는
 * 하한 날짜 계산을 고정한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class RetentionScheduleServiceTest {

    @Autowired
    private RetentionScheduleService retentionScheduleService;

    @Autowired
    private RetentionPolicyService retentionPolicyService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Test
    @DisplayName("마감된 공고가 없으면 9999-12-31")
    void noClosedPostingMeansNoSchedule() {
        assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    @DisplayName("전역 정책이 없으면 9999-12-31")
    void noPolicyMeansNoSchedule() {
        // given: 마감된 공고 + 지원서(정책은 만들지 않는다)
        Long jobPostingId = createClosedPosting(LocalDateTime.of(2024, 3, 10, 9, 0));
        createApplication(jobPostingId, "schedule-nopolicy");

        assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    @DisplayName("가장 이른 마감일 + 보존기간이 예정일이다")
    void earliestClosedAtPlusPeriod() {
        // given: 전역 정책 1825일(CLOSED_AT 기준)
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 1825, RetentionBaselineType.CLOSED_AT, true, null, null), "privacy01");

        // 마감일 2024-03-10T09:00 공고 + 미파기 지원서
        Long earlierPostingId = createClosedPosting(LocalDateTime.of(2024, 3, 10, 9, 0));
        createApplication(earlierPostingId, "schedule-earlier");

        // 마감일 2025-06-01T09:00 공고 + 미파기 지원서
        Long laterPostingId = createClosedPosting(LocalDateTime.of(2025, 6, 1, 9, 0));
        createApplication(laterPostingId, "schedule-later");

        assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(2029, 3, 9));
    }

    @Test
    @DisplayName("이미 파기된 지원서만 있는 공고는 계산에서 제외한다")
    void purgedApplicationsExcluded() {
        // given: 전역 정책 1825일 + 마감 공고 1개, 그 지원서를 markPurged 처리
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 1825, RetentionBaselineType.CLOSED_AT, true, null, null), "privacy01");

        Long jobPostingId = createClosedPosting(LocalDateTime.of(2024, 3, 10, 9, 0));
        JobApplication application = createApplication(jobPostingId, "schedule-purged");
        application.markPurged(1L, LocalDateTime.of(2024, 4, 10, 9, 0));
        jobApplicationRepository.save(application);

        assertThat(retentionScheduleService.nextPurgeDate()).isEqualTo(LocalDate.of(9999, 12, 31));
    }

    @Test
    @DisplayName("설정 행이 없으면 꺼짐으로 본다")
    void defaultsToDisabled() {
        RetentionScheduleResponse response = retentionScheduleService.getSchedule();

        assertThat(response.enabled()).isFalse();
        assertThat(response.lastRunAt()).isNull();
    }

    @Test
    @DisplayName("토글하면 행을 만들고 감사를 남긴다")
    void toggleCreatesRowAndAudit() {
        // given: 전역 정책 생성(1825일, CLOSED_AT)
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 1825, RetentionBaselineType.CLOSED_AT, true, null, null), "privacy01");

        RetentionScheduleResponse response = retentionScheduleService.updateEnabled(true, "tester");

        assertThat(response.enabled()).isTrue();
        // 위 정책 생성(CREATE)도 같은 actionType 으로 감사를 남기므로 SCHEDULE_ENABLE 건만 걸러 확인한다.
        List<ActivityLog> scheduleLogs = search(AuditActionType.RETENTION_POLICY_UPDATE).stream()
                .filter(log -> log.getMetadataJson().contains("SCHEDULE_ENABLE"))
                .toList();
        assertThat(scheduleLogs).hasSize(1);
        assertThat(scheduleLogs.get(0).getTargetType()).isEqualTo(AuditTargetType.RETENTION_SCHEDULE);
    }

    @Test
    @DisplayName("정책이 없으면 켤 수 없다")
    void cannotEnableWithoutPolicy() {
        assertThatThrownBy(() -> retentionScheduleService.updateEnabled(true, "tester"))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    @Test
    @DisplayName("actor 가 비면 토글을 거부한다")
    void toggleRequiresActor() {
        assertThatThrownBy(() -> retentionScheduleService.updateEnabled(true, " "))
                .isInstanceOf(InvalidRetentionRequestException.class);
    }

    @Test
    @DisplayName("응답에 정책 유무와 보존기간이 함께 담긴다")
    void responseCarriesPolicyInfo() {
        // given: 전역 정책 생성(1825일, CLOSED_AT)
        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 1825, RetentionBaselineType.CLOSED_AT, true, null, null), "privacy01");

        RetentionScheduleResponse response = retentionScheduleService.getSchedule();

        assertThat(response.hasPolicy()).isTrue();
        assertThat(response.retentionPeriodDays()).isEqualTo(1825);
    }

    @Test
    @DisplayName("recordRun은 설정 행을 만들고 실행 결과를 남기되 감사는 남기지 않는다")
    void recordRunPersistsResultWithoutAudit() {
        retentionScheduleService.recordRun(RetentionScheduleRunResult.EXECUTED, 42L);

        RetentionScheduleResponse response = retentionScheduleService.getSchedule();
        assertThat(response.lastRunResult()).isEqualTo(RetentionScheduleRunResult.EXECUTED);
        assertThat(response.lastRunBatchId()).isEqualTo(42L);
        assertThat(response.lastRunAt()).isNotNull();
        // dry-run·execute 가 각자 감사를 남기므로 recordRun 자체는 감사를 남기지 않는다.
        assertThat(search(AuditActionType.RETENTION_POLICY_UPDATE)).isEmpty();
    }

    @Test
    @DisplayName("isEnabled는 설정 행이 없으면 false, 토글 후에는 true")
    void isEnabledReflectsSetting() {
        assertThat(retentionScheduleService.isEnabled()).isFalse();

        retentionPolicyService.create(new RetentionPolicyRequest(
                null, 1825, RetentionBaselineType.CLOSED_AT, true, null, null), "privacy01");
        retentionScheduleService.updateEnabled(true, "tester");

        assertThat(retentionScheduleService.isEnabled()).isTrue();
    }

    private List<ActivityLog> search(AuditActionType actionType) {
        return activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, actionType, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
    }

    private Long createClosedPosting(LocalDateTime closedAt) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "retention schedule recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        jobPosting.close(closedAt);
        jobPostingRepository.save(jobPosting);
        return jobPostingId;
    }

    private JobApplication createApplication(Long jobPostingId, String loginId) {
        Applicant applicant = new Applicant(loginId + "-ci", HashUtil.sha256(loginId + "-ci"));
        applicant.setLoginId(loginId);
        applicant.setName("User " + loginId);
        applicant.setUserName("Applicant " + loginId);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, jobPosting, jobPosition, "name", "title", "pos");
        return jobApplicationRepository.save(application);
    }
}
