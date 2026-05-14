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
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobApplicationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void published_and_accepting_job_posting_application_create_success() {
        Applicant applicant = createApplicant("applicant-create", "지원자A");
        Long jobPostingId = createPublishedJobPosting();
        Long jobPositionId = firstJobPositionId(jobPostingId);

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        );

        ApplicationDetailResponse detail = jobApplicationService.getApplication(applicant.getId(), applicationId);
        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThat(detail.applicantId()).isEqualTo(applicant.getId());
        assertThat(detail.status()).isEqualTo(JobApplicationStatus.DRAFT);
    }

    @Test
    void created_application_status_is_draft() {
        Applicant applicant = createApplicant("applicant-draft", "지원자B");
        Long jobPostingId = createPublishedJobPosting();

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(JobApplicationStatus.DRAFT);
        assertThat(application.getSubmittedAt()).isNull();
        assertThat(application.getWithdrawnAt()).isNull();
    }

    @Test
    void create_application_saves_snapshots() {
        Applicant applicant = createApplicant("applicant-snapshot", "지원자스냅샷");
        Long jobPostingId = createPublishedJobPosting("스냅샷 공고");
        Long jobPositionId = firstJobPositionId(jobPostingId);

        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        );

        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getApplicantNameSnapshot()).isEqualTo("지원자스냅샷");
        assertThat(application.getJobPostingTitleSnapshot()).isEqualTo("스냅샷 공고");
        assertThat(application.getJobPositionNameSnapshot()).isEqualTo("Backend");
    }

    @Test
    void create_application_fails_when_job_posting_is_draft() {
        Applicant applicant = createApplicant("applicant-draft-posting", "지원자C");
        Long jobPostingId = createDraftJobPosting();

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_job_posting_is_closed() {
        Applicant applicant = createApplicant("applicant-closed-posting", "지원자D");
        Long jobPostingId = createPublishedJobPosting();
        jobPostingService.close(jobPostingId);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_before_reception_start() {
        Applicant applicant = createApplicant("applicant-before", "지원자E");
        Long jobPostingId = createPublishedJobPosting(
                "접수전 공고",
                LocalDateTime.of(2026, 6, 16, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_after_reception_end() {
        Applicant applicant = createApplicant("applicant-after", "지원자F");
        Long jobPostingId = createPublishedJobPosting(
                "접수후 공고",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 14, 18, 0)
        );

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_applicant_not_found() {
        Long jobPostingId = createPublishedJobPosting();

        assertThatThrownBy(() -> jobApplicationService.create(
                99999L,
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_job_posting_not_found() {
        Applicant applicant = createApplicant("applicant-no-posting", "지원자G");

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(99999L, 1L)
        )).isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void create_application_fails_when_job_position_not_found() {
        Applicant applicant = createApplicant("applicant-no-position", "지원자H");
        Long jobPostingId = createPublishedJobPosting();

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, 99999L)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_job_position_belongs_to_other_job_posting() {
        Applicant applicant = createApplicant("applicant-other-position", "지원자I");
        Long firstJobPostingId = createPublishedJobPosting("첫 번째 공고");
        Long secondJobPostingId = createPublishedJobPosting("두 번째 공고");
        Long otherJobPositionId = firstJobPositionId(secondJobPostingId);

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(firstJobPostingId, otherJobPositionId)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_application_form_config_is_missing() {
        Applicant applicant = createApplicant("applicant-no-config", "지원자J");
        JobPosting jobPosting = JobPosting.create(
                "설정 없는 공고",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Backend", 1, 0)));
        jobPosting.publish(LocalDateTime.of(2026, 6, 1, 10, 0));
        Long jobPostingId = jobPostingRepository.save(jobPosting).getId();

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_duplicate_application_exists() {
        Applicant applicant = createApplicant("applicant-duplicate", "지원자K");
        Long jobPostingId = createPublishedJobPosting();
        Long jobPositionId = firstJobPositionId(jobPostingId);
        jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionId));

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void create_application_fails_when_same_job_posting_has_other_position() {
        Applicant applicant = createApplicant("applicant-other-position-same-posting", "지원자L");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(0)));

        assertThatThrownBy(() -> jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionIds.get(1))
        )).isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void get_my_application_success() {
        Applicant applicant = createApplicant("applicant-get", "지원자M");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        ApplicationDetailResponse detail = jobApplicationService.getApplication(applicant.getId(), applicationId);

        assertThat(detail.applicationId()).isEqualTo(applicationId);
        assertThat(detail.jobPostingId()).isEqualTo(jobPostingId);
    }

    @Test
    void get_application_fails_when_owned_by_other_applicant() {
        Applicant applicant = createApplicant("applicant-owner", "지원자N");
        Applicant otherApplicant = createApplicant("applicant-other", "지원자O");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        assertThatThrownBy(() -> jobApplicationService.getApplication(otherApplicant.getId(), applicationId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void get_my_application_by_job_posting_success() {
        Applicant applicant = createApplicant("applicant-by-posting", "지원자P");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );

        ApplicationDetailResponse detail = jobApplicationService.getMyApplicationByJobPosting(applicant.getId(), jobPostingId);

        assertThat(detail.applicationId()).isEqualTo(applicationId);
    }

    @Test
    void get_my_application_by_job_posting_fails_when_not_found() {
        Applicant applicant = createApplicant("applicant-by-posting-not-found", "지원자Q");
        Long jobPostingId = createPublishedJobPosting();

        assertThatThrownBy(() -> jobApplicationService.getMyApplicationByJobPosting(applicant.getId(), jobPostingId))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    private Applicant createApplicant(String loginId, String applicantName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("상위-" + applicantName);
        applicant.setUserName(applicantName);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createDraftJobPosting() {
        return jobPostingService.create(createJobPostingRequest(
                "2026 recruitment",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        ));
    }

    private Long createPublishedJobPosting() {
        return createPublishedJobPosting("2026 recruitment");
    }

    private Long createPublishedJobPosting(String title) {
        return createPublishedJobPosting(
                title,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0)
        );
    }

    private Long createPublishedJobPosting(String title, LocalDateTime start, LocalDateTime end) {
        Long jobPostingId = jobPostingService.create(createJobPostingRequest(title, start, end));
        jobPostingService.publish(jobPostingId);
        assertThat(jobPostingRepository.findById(jobPostingId).orElseThrow().getStatus()).isEqualTo(JobPostingStatus.PUBLISHED);
        return jobPostingId;
    }

    private JobPostingCreateRequest createJobPostingRequest(String title, LocalDateTime start, LocalDateTime end) {
        return new JobPostingCreateRequest(
                title,
                "<p>content</p>",
                start,
                end,
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private Long firstJobPositionId(Long jobPostingId) {
        return jobPositionIds(jobPostingId).get(0);
    }

    private List<Long> jobPositionIds(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        return jobPosting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .map(JobPosition::getId)
                .toList();
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
