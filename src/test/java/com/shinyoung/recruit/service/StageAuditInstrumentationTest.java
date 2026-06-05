package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 09b — StageResult 발표(announce)/확정(close)/정정(correct) in-tx 감사 계측 검증.
 *
 * <p>기존 {@code StageControllerTest}의 announce/close 픽스처는 접수기간이 2026-05로 하드코딩되어
 * 날짜 의존 사전-실패한다(알려진 한계). 본 테스트는 접수기간을 현재 기준 동적으로 잡아 계측 경로를 실증한다.
 * 직접 서비스 호출이라 SecurityContext 가 없으므로 announce/close 의 actorType 은 ANONYMOUS 로 기록된다
 * (실요청 경로에서는 admin matcher 를 통과한 EMPLOYEE).
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class StageAuditInstrumentationTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Test
    void 발표_확정_정정이_ActivityLog에_in_tx로_기록된다() {
        Long jobPostingId = createJobPosting();
        Long stageId = stageService.create(jobPostingId, new StageCreateRequest(
                "Document screening", StageType.DOCUMENT, 0, LocalDateTime.now().plusDays(40), false));
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);

        createSubmittedApplication("audit-stage-1", jobPostingId);
        stageResultService.initialize(stageId);
        for (AdminStageResultResponse result : stageResultService.getResults(stageId)) {
            stageResultService.updateResult(
                    stageId, result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                    "employee01");
        }

        stageService.announce(jobPostingId, stageId);
        stageService.close(jobPostingId, stageId);

        // 정정(updateResult) — actor 파라미터 fallback 으로 EMPLOYEE 기록 + CorrectionHistory 참조용 targetId.
        List<ActivityLog> corrects = search(AuditActionType.STAGE_RESULT_CORRECT);
        assertThat(corrects).hasSize(1);
        assertThat(corrects.get(0).getActorType()).isEqualTo(ActorType.EMPLOYEE);
        assertThat(corrects.get(0).getActorId()).isEqualTo("employee01");
        assertThat(corrects.get(0).getJobPostingId()).isEqualTo(jobPostingId);
        assertThat(corrects.get(0).getApplicationId()).isNotNull();
        assertThat(corrects.get(0).getMetadataJson()).contains("\"changedCount\":1");

        // 발표(announce).
        List<ActivityLog> announces = search(AuditActionType.STAGE_RESULT_ANNOUNCE);
        assertThat(announces).hasSize(1);
        assertThat(announces.get(0).getActionResult()).isEqualTo(AuditActionResult.SUCCESS);
        assertThat(announces.get(0).getTargetType()).isEqualTo(AuditTargetType.STAGE_RESULT);
        assertThat(announces.get(0).getTargetId()).isEqualTo(String.valueOf(stageId));
        assertThat(announces.get(0).getJobPostingId()).isEqualTo(jobPostingId);

        // 확정(close → STAGE_RESULT_CONFIRM).
        List<ActivityLog> confirms = search(AuditActionType.STAGE_RESULT_CONFIRM);
        assertThat(confirms).hasSize(1);
        assertThat(confirms.get(0).getTargetId()).isEqualTo(String.valueOf(stageId));
    }

    private List<ActivityLog> search(AuditActionType actionType) {
        return activityLogRepository.search(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, actionType, null, null, null, null,
                PageRequest.of(0, 10)).getContent();
    }

    private Long createJobPosting() {
        // 접수기간을 현재 기준 동적으로 잡아 날짜 의존 사전-실패를 피한다.
        return jobPostingService.create(new JobPostingCreateRequest(
                "audit instrumentation recruitment",
                "<p>content</p>",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private void createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = createApplicant(loginId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        jobApplicationService.submit(applicant.getId(), applicationId);
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
