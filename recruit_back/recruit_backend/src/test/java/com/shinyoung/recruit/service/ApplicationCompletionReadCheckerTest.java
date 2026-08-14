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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationCompletionReadCheckerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired
    private ApplicationCompletionReadChecker completionReadChecker;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Test
    void basic_info_is_required_group_and_missing_when_absent() {
        Applicant applicant = createApplicant("cmp-missing", "Missing");
        Long applicationId = createApplication(applicant);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        var result = completionReadChecker.check(application);

        assertThat(result.requiredMissingSections())
                .anyMatch(section -> section.sectionCode().equals("BASIC_INFO"));
    }

    @Test
    void basic_info_not_missing_when_present() {
        Applicant applicant = createApplicant("cmp-present", "Present");
        Long applicationId = createApplication(applicant);
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, application);

        var result = completionReadChecker.check(application);

        assertThat(result.requiredMissingSections())
                .noneMatch(section -> section.sectionCode().equals("BASIC_INFO"));
    }

    // ---- fixtures ----

    private Applicant createApplicant(String loginId, String userName) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName("User-" + userName);
        applicant.setUserName(userName);
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.save(applicant);
    }

    private Long createApplication(Applicant applicant) {
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
        return jobApplicationService.create(applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
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
