package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationSectionAccessServiceTest {

    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final ApplicationSectionAccessService service = new ApplicationSectionAccessService(
            jobApplicationRepository,
            Clock.systemUTC()
    );

    @Test
    void section_access_checks_use_flag_not_require_flag() {
        ApplicationFormConfig config = mock(ApplicationFormConfig.class);
        when(config.isUseEducation()).thenReturn(true);

        assertThatCode(() -> service.validateEducationEnabled(application(config)))
                .doesNotThrowAnyException();

        verify(config, never()).isRequireEducation();
    }

    private JobApplication application(ApplicationFormConfig config) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getApplicationFormConfig()).thenReturn(config);

        JobApplication application = mock(JobApplication.class);
        when(application.getJobPosting()).thenReturn(jobPosting);
        return application;
    }
}
