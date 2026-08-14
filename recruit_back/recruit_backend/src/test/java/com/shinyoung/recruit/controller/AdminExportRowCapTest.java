package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.export.max-rows=1"
})
@Transactional
class AdminExportRowCapTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void export_rejects_when_row_count_exceeds_max_rows_without_generating_workbook() throws Exception {
        Long jobPostingId = createJobPosting();
        persistApplication(jobPostingId, "cap-1", "Cap One");
        persistApplication(jobPostingId, "cap-2", "Cap Two");

        mockMvc.perform(get("/api/admin/applications/export")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("EXPORT_ROW_LIMIT_EXCEEDED")));
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "row-cap",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private void persistApplication(Long jobPostingId, String loginId, String name) {
        String ci = loginId + "-ci";
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId(loginId);
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(loginId + "@example.com");
        applicantRepository.save(applicant);

        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .min(Comparator.comparing(JobPosition::getSortOrder).thenComparing(JobPosition::getId))
                .orElseThrow();

        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                name,
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        jobApplicationRepository.save(application);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "rowcap-admin",
                "Recruit",
                "Row Cap Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
