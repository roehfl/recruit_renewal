package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.ApplicantStageResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationStageResultServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private ApplicationStageResultService applicationStageResultService;

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
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void applicant_can_read_result_announced_stage_result() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-read", "Applicant Result Read");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        Long resultId = decideResult(jobPostingId, stageId, StageResultStatus.PASSED, new BigDecimal("95.5"), "internal");
        stageService.announce(jobPostingId, stageId);

        List<ApplicantStageResultResponse> responses = applicationStageResultService.getApplicantStageResults(
                applicant.getId(),
                applicationId
        );

        assertThat(responses).hasSize(1);
        ApplicantStageResultResponse response = responses.get(0);
        assertThat(response.stageName()).isEqualTo("Stage 0");
        assertThat(response.stageType()).isEqualTo(StageType.DOCUMENT);
        assertThat(response.stageOrder()).isZero();
        assertThat(response.resultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(response.resultAnnouncementDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 10, 0));
        assertThat(response.decidedAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0));
        assertThat(resultId).isNotNull();
    }

    @Test
    void applicant_can_read_closed_stage_result() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-closed", "Applicant Result Closed");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        decideResult(jobPostingId, stageId, StageResultStatus.FAILED, null, null);
        stageService.announce(jobPostingId, stageId);
        stageService.close(jobPostingId, stageId);

        List<ApplicantStageResultResponse> responses = applicationStageResultService.getApplicantStageResults(
                applicant.getId(),
                applicationId
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).resultStatus()).isEqualTo(StageResultStatus.FAILED);
    }

    @Test
    void ready_and_in_progress_stage_results_are_not_exposed() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-hidden", "Applicant Result Hidden");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long readyStageId = createStage(jobPostingId, 0, false, LocalDateTime.of(2026, 7, 1, 10, 0));
        Long inProgressStageId = createStage(jobPostingId, 1, true, LocalDateTime.of(2026, 7, 2, 10, 0));

        stageResultService.initialize(readyStageId);
        decideResult(jobPostingId, inProgressStageId, StageResultStatus.PASSED, null, null);

        List<ApplicantStageResultResponse> responses = applicationStageResultService.getApplicantStageResults(
                applicant.getId(),
                applicationId
        );

        assertThat(responses).isEmpty();
    }

    @Test
    void draft_application_cannot_read_stage_results() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-draft", "Applicant Result Draft");
        Long applicationId = createApplication(applicant, jobPostingId);

        assertThatThrownBy(() -> applicationStageResultService.getApplicantStageResults(applicant.getId(), applicationId))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void withdrawn_application_can_read_announced_stage_result() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-withdrawn", "Applicant Result Withdrawn");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        decideResult(jobPostingId, stageId, StageResultStatus.PASSED, null, null);
        stageService.announce(jobPostingId, stageId);
        jobApplicationService.withdraw(applicant.getId(), applicationId);

        List<ApplicantStageResultResponse> responses = applicationStageResultService.getApplicantStageResults(
                applicant.getId(),
                applicationId
        );
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.WITHDRAWN);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).resultStatus()).isEqualTo(StageResultStatus.PASSED);
    }

    @Test
    void other_applicants_application_is_hidden() {
        Long jobPostingId = createJobPosting();
        Applicant owner = createApplicant("applicant-result-owner", "Applicant Result Owner");
        Applicant other = createApplicant("applicant-result-other", "Applicant Result Other");
        Long applicationId = createSubmittedApplication(owner, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 7, 1, 10, 0));
        decideResult(jobPostingId, stageId, StageResultStatus.PASSED, null, null);
        stageService.announce(jobPostingId, stageId);

        assertThatThrownBy(() -> applicationStageResultService.getApplicantStageResults(other.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void applicant_response_does_not_define_internal_fields() {
        List<String> fieldNames = Arrays.stream(ApplicantStageResultResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames)
                .containsExactly(
                        "stageName",
                        "stageType",
                        "stageOrder",
                        "resultStatus",
                        "resultAnnouncementDateTime",
                        "decidedAt"
                )
                .doesNotContain(
                        "stageResultId",
                        "score",
                        "comment",
                        "decidedBy",
                        "correctedBy",
                        "histories",
                        "createdAt",
                        "updatedAt"
                );
    }

    @Test
    void future_result_announcement_date_time_does_not_block_announced_stage_result() {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("applicant-result-future", "Applicant Result Future");
        Long applicationId = createSubmittedApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true, LocalDateTime.of(2026, 12, 1, 10, 0));
        decideResult(jobPostingId, stageId, StageResultStatus.PASSED, null, null);
        stageService.announce(jobPostingId, stageId);

        List<ApplicantStageResultResponse> responses = applicationStageResultService.getApplicantStageResults(
                applicant.getId(),
                applicationId
        );

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).resultAnnouncementDateTime()).isEqualTo(LocalDateTime.of(2026, 12, 1, 10, 0));
    }

    private Long createJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 Applicant StageResult recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage, LocalDateTime announcementAt) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Stage " + stageOrder,
                StageType.DOCUMENT,
                stageOrder,
                announcementAt,
                finalStage
        ));
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
    }

    private Long createSubmittedApplication(Applicant applicant, Long jobPostingId) {
        Long applicationId = createApplication(applicant, jobPostingId);
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
    }

    private Long decideResult(
            Long jobPostingId,
            Long stageId,
            StageResultStatus status,
            BigDecimal score,
            String comment
    ) {
        stageService.start(jobPostingId, stageId);
        stageResultService.initialize(stageId);
        Long resultId = stageResultService.getResults(stageId).get(0).stageResultId();
        stageResultService.updateResult(stageId, resultId, new StageResultUpdateRequest(status, score, comment), "employee01");
        return resultId;
    }

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + applicantName);
        applicant.setUserName(applicantName);
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

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
