package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationSubmitValidatorBasicInfoTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired private ApplicationSubmitValidator submitValidator;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;

    @Test
    void submit_validation_fails_without_basic_info() {
        JobApplication application = newApplication();
        assertThatThrownBy(() -> submitValidator.validate(application))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void submit_validation_passes_with_basic_info() {
        JobApplication application = newApplication();
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, application);
        assertThatCode(() -> submitValidator.validate(application)).doesNotThrowAnyException();
    }

    private JobApplication newApplication() {
        Applicant applicant = new Applicant("submit-ci", HashUtil.sha256("submit-ci"));
        applicant.setLoginId("submit-applicant");
        applicant.setName("User-Submit");
        applicant.setUserName("Submit");
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment", "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0), LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 0)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        jobPostingService.publish(jobPostingId);
        JobPosting posting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long positionId = posting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder)).map(JobPosition::getId)
                .findFirst().orElseThrow();
        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
        return jobApplicationRepository.findById(applicationId).orElseThrow();
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
