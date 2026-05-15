package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
class ApplicationControllerTest {

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
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-create", "Api Create");
        authenticate(applicant);
        Long jobPostingId = createPublishedJobPosting();
        Long jobPositionId = firstJobPositionId(jobPostingId);

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson(jobPostingId, jobPositionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void create_application_validation_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-create-validation", "Api Create Validation");
        authenticate(applicant);

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": null,
                                  "jobPositionId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void employee_user_cannot_create_application() throws Exception {
        Employee employee = new Employee();
        employee.setLoginId("employee-api");
        employee.setName("Employee Api");
        authenticate(employee);
        Long jobPostingId = createPublishedJobPosting();
        Long jobPositionId = firstJobPositionId(jobPostingId);

        mockMvc.perform(post("/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson(jobPostingId, jobPositionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-get", "Api Get");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.applicantId").value(applicant.getId()))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId));
    }

    @Test
    void get_application_not_found_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-get-not-found", "Api Get Not Found");
        authenticate(applicant);

        mockMvc.perform(get("/applications/{applicationId}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-get-owner", "Api Get Owner");
        Applicant other = createApplicant("api-get-other", "Api Get Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        authenticate(other);

        mockMvc.perform(get("/applications/{applicationId}", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void update_draft_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-update", "Api Update");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId, jobPositionIds.get(0));
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateApplicationJson(jobPositionIds.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(applicationId));
    }

    @Test
    void update_draft_validation_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-update-validation", "Api Update Validation");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPositionId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void update_draft_invalid_state_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-update-state", "Api Update State");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId, jobPositionIds.get(0));
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateApplicationJson(jobPositionIds.get(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void update_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-update-owner", "Api Update Owner");
        Applicant other = createApplicant("api-update-other", "Api Update Other");
        Long jobPostingId = createPublishedJobPosting();
        List<Long> jobPositionIds = jobPositionIds(jobPostingId);
        Long applicationId = createApplication(owner, jobPostingId, jobPositionIds.get(0));
        authenticate(other);

        mockMvc.perform(post("/applications/{applicationId}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateApplicationJson(jobPositionIds.get(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submit_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-submit", "Api Submit");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(applicationId));
    }

    @Test
    void submit_invalid_state_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-submit-state", "Api Submit State");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submit_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-submit-owner", "Api Submit Owner");
        Applicant other = createApplicant("api-submit-other", "Api Submit Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        authenticate(other);

        mockMvc.perform(post("/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void withdraw_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-withdraw", "Api Withdraw");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/withdraw", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(applicationId));
    }

    @Test
    void withdraw_invalid_state_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-withdraw-state", "Api Withdraw State");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        authenticate(applicant);

        mockMvc.perform(post("/applications/{applicationId}/withdraw", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void withdraw_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-withdraw-owner", "Api Withdraw Owner");
        Applicant other = createApplicant("api-withdraw-other", "Api Withdraw Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        jobApplicationService.submit(owner.getId(), applicationId);
        authenticate(other);

        mockMvc.perform(post("/applications/{applicationId}/withdraw", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_my_application_by_job_posting_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-by-posting", "Api By Posting");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(get("/job-postings/{jobPostingId}/application", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId));
    }

    @Test
    void get_my_application_by_job_posting_not_found_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-by-posting-not-found", "Api By Posting Not Found");
        Long jobPostingId = createPublishedJobPosting();
        authenticate(applicant);

        mockMvc.perform(get("/job-postings/{jobPostingId}/application", jobPostingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_method_is_not_supported_for_application_update() throws Exception {
        mockMvc.perform(put("/applications/{applicationId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_http_method_is_not_supported_for_application_delete() throws Exception {
        mockMvc.perform(delete("/applications/{applicationId}", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    private void authenticate(Applicant applicant) {
        authenticate(CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        ));
    }

    private void authenticate(Employee employee) {
        authenticate(CustomUserDetails.fromUser(
                employee,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        ));
    }

    private void authenticate(CustomUserDetails userDetails) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
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

    private Long createPublishedJobPosting() {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 2, 0),
                        new JobPositionRequest("Frontend", 1, 1)
                ),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
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

    private String createApplicationJson(Long jobPostingId, Long jobPositionId) {
        return """
                {
                  "jobPostingId": %d,
                  "jobPositionId": %d
                }
                """.formatted(jobPostingId, jobPositionId);
    }

    private String updateApplicationJson(Long jobPositionId) {
        return """
                {
                  "jobPositionId": %d
                }
                """.formatted(jobPositionId);
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
