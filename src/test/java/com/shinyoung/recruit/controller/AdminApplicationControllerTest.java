package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class AdminApplicationControllerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-15T10:00:00Z"),
            ZoneId.of("UTC")
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobApplicationService jobApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void get_admin_applications_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("admin-api-list", "Admin Api List");
        Long jobPostingId = createPublishedJobPosting("Admin Api List Posting");
        Long applicationId = createApplication(applicant, jobPostingId);

        mockMvc.perform(get("/admin/applications")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.content[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.content[0].applicantNameSnapshot").value("Admin Api List"))
                .andExpect(jsonPath("$.data.content[0].jobPostingTitleSnapshot").value("Admin Api List Posting"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void get_admin_applications_filters_by_status_job_posting_and_position() throws Exception {
        Applicant draftApplicant = createApplicant("admin-api-filter-draft", "Admin Api Filter Draft");
        Applicant submittedApplicant = createApplicant("admin-api-filter-submitted", "Admin Api Filter Submitted");
        Long jobPostingId = createPublishedJobPosting("Admin Api Filter Posting");
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long draftApplicationId = createApplication(draftApplicant, jobPostingId, jobPositionIds.get(0));
        Long submittedApplicationId = createApplication(submittedApplicant, jobPostingId, jobPositionIds.get(1));
        jobApplicationService.submit(submittedApplicant.getId(), submittedApplicationId);

        mockMvc.perform(get("/admin/applications")
                        .param("status", " draft "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].applicationId").value(draftApplicationId));

        mockMvc.perform(get("/admin/applications")
                        .param("jobPostingId", String.valueOf(jobPostingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/admin/applications")
                        .param("jobPositionId", String.valueOf(jobPositionIds.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].applicationId").value(submittedApplicationId));
    }

    @Test
    void get_admin_application_detail_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("admin-api-detail", "Admin Api Detail");
        Long jobPostingId = createPublishedJobPosting("Admin Api Detail Posting");
        Long applicationId = createApplication(applicant, jobPostingId);

        mockMvc.perform(get("/admin/applications/{applicationId}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.applicantId").value(applicant.getId()))
                .andExpect(jsonPath("$.data.applicantNameSnapshot").value("Admin Api Detail"))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId));
    }

    @Test
    void get_admin_application_detail_not_found_returns_api_response() throws Exception {
        mockMvc.perform(get("/admin/applications/{applicationId}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_admin_applications_by_job_posting_returns_api_response() throws Exception {
        Applicant firstApplicant = createApplicant("admin-api-posting-first", "Admin Api Posting First");
        Applicant secondApplicant = createApplicant("admin-api-posting-second", "Admin Api Posting Second");
        Applicant otherApplicant = createApplicant("admin-api-posting-other", "Admin Api Posting Other");
        Long jobPostingId = createPublishedJobPosting("Admin Api Posting Applications");
        Long otherJobPostingId = createPublishedJobPosting("Admin Api Posting Other");
        Long firstApplicationId = createApplication(firstApplicant, jobPostingId);
        createApplication(secondApplicant, jobPostingId);
        createApplication(otherApplicant, otherJobPostingId);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/applications", jobPostingId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].applicationId").value(org.hamcrest.Matchers.hasItem(firstApplicationId.intValue())));
    }

    @Test
    void invalid_admin_application_query_returns_api_response() throws Exception {
        mockMvc.perform(get("/admin/applications")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/admin/applications")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/admin/applications")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/admin/applications")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void admin_application_write_methods_are_not_supported() throws Exception {
        mockMvc.perform(put("/admin/applications/{applicationId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/admin/applications/{applicationId}", 1L))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/admin/applications/{applicationId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private Long createApplication(Applicant applicant, Long jobPostingId) {
        return createApplication(applicant, jobPostingId, firstJobPositionId(jobPostingId));
    }

    private Long createApplication(Applicant applicant, Long jobPostingId, Long jobPositionId) {
        return jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, jobPositionId)
        );
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

    private Long createPublishedJobPosting(String title) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                title,
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
