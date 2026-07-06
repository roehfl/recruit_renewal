package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.support.BasicInfoTestSupport;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPostingQuestionCreateRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.JobPostingQuestionService;
import com.shinyoung.recruit.service.StageResultService;
import com.shinyoung.recruit.service.StageService;
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
import org.springframework.security.core.Authentication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private JobPostingQuestionService jobPostingQuestionService;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private StageService stageService;

    @Autowired
    private StageResultService stageResultService;

    @Autowired
    private ApplicationBasicInfoRepository basicInfoRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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

        mockMvc.perform(post("/api/applications")
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

        mockMvc.perform(post("/api/applications")
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

        // 인증은 됐지만 지원자 타입이 아님 → 403 (AccessForbiddenException)
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson(jobPostingId, jobPositionId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-get", "Api Get");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = createApplication(applicant, jobPostingId);
        authenticate(applicant);

        mockMvc.perform(get("/api/applications/{applicationId}", applicationId))
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

        mockMvc.perform(get("/api/applications/{applicationId}", 99999L))
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

        mockMvc.perform(get("/api/applications/{applicationId}", applicationId))
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

        mockMvc.perform(post("/api/applications/{applicationId}", applicationId)
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

        mockMvc.perform(post("/api/applications/{applicationId}", applicationId)
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
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}", applicationId)
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

        mockMvc.perform(post("/api/applications/{applicationId}", applicationId)
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
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(applicationId));
    }

    @Test
    void submit_invalid_state_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-submit-state", "Api Submit State");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submit_detail_validation_failure_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-submit-detail-validation", "Api Submit Detail Validation");
        Long jobPostingId = createPublishedJobPosting(new ApplicationFormConfigRequest(
                true,
                false,
                false,
                false,
                false,
                false,
                false
        ));
        Long applicationId = createApplication(applicant, jobPostingId);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submit_required_question_answer_missing_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-submit-answer-missing", "Api Submit Answer Missing");
        Long jobPostingId = createDraftJobPosting(new ApplicationFormConfigRequest(
                false,
                false,
                false,
                false,
                false,
                false,
                false
        ));
        createQuestion(jobPostingId, true, QuestionAnswerType.LONG_TEXT, 1000);
        jobPostingService.publish(jobPostingId);
        Long applicationId = createApplication(applicant, jobPostingId);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void submit_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-submit-owner", "Api Submit Owner");
        Applicant other = createApplicant("api-submit-other", "Api Submit Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        authenticate(other);

        mockMvc.perform(post("/api/applications/{applicationId}/submit", applicationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void withdraw_application_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-withdraw", "Api Withdraw");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        authenticate(applicant);

        mockMvc.perform(post("/api/applications/{applicationId}/withdraw", applicationId))
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

        mockMvc.perform(post("/api/applications/{applicationId}/withdraw", applicationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void withdraw_other_applicants_application_is_hidden() throws Exception {
        Applicant owner = createApplicant("api-withdraw-owner", "Api Withdraw Owner");
        Applicant other = createApplicant("api-withdraw-other", "Api Withdraw Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(owner.getId(), applicationId);
        authenticate(other);

        mockMvc.perform(post("/api/applications/{applicationId}/withdraw", applicationId))
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

        mockMvc.perform(get("/api/job-postings/{jobPostingId}/application", jobPostingId))
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

        mockMvc.perform(get("/api/job-postings/{jobPostingId}/application", jobPostingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_my_applications_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-my-list", "Api My List");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = createApplication(applicant, jobPostingId);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        Long stageId = createStage(jobPostingId, 0, true);
        decideAndAnnounce(jobPostingId, stageId, StageResultStatus.PASSED);
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/me")
                        .param("page", "0")
                        .param("size", "20")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.content[0].jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.content[0].jobPostingTitle").value("2026 recruitment"))
                .andExpect(jsonPath("$.data.content[0].jobPostingStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].jobPositionId").isNumber())
                .andExpect(jsonPath("$.data.content[0].jobPositionName").value("Backend"))
                .andExpect(jsonPath("$.data.content[0].applicationStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.content[0].submittedAt").exists())
                .andExpect(jsonPath("$.data.content[0].withdrawnAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].receptionStartDateTime").exists())
                .andExpect(jsonPath("$.data.content[0].receptionEndDateTime").exists())
                .andExpect(jsonPath("$.data.content[0].accepting").value(true))
                .andExpect(jsonPath("$.data.content[0].announcedResultCount").value(1))
                .andExpect(jsonPath("$.data.content[0].latestAnnouncedStageName").value("Stage 0"))
                .andExpect(jsonPath("$.data.content[0].latestResultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.content[0].applicantId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].score").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].comment").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].decidedBy").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].correctedBy").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].histories").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void get_application_dashboard_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-dashboard", "Api Dashboard");
        Long jobPostingId = createPublishedJobPosting();
        Long applicationId = createApplication(applicant, jobPostingId);
        Long stageId = createStage(jobPostingId, 0, true);
        BasicInfoTestSupport.seedValidBasicInfo(basicInfoRepository, jobApplicationRepository.findById(applicationId).orElseThrow());
        jobApplicationService.submit(applicant.getId(), applicationId);
        decideAndAnnounce(jobPostingId, stageId, StageResultStatus.PASSED);
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.jobPostingTitle").value("2026 recruitment"))
                .andExpect(jsonPath("$.data.jobPositionName").value("Backend"))
                .andExpect(jsonPath("$.data.applicationStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.accepting").value(true))
                .andExpect(jsonPath("$.data.editable").value(false))
                .andExpect(jsonPath("$.data.submittable").value(false))
                .andExpect(jsonPath("$.data.withdrawable").value(true))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.withdrawnAt").doesNotExist())
                .andExpect(jsonPath("$.data.completionSummary.requiredSectionCount").value(0))
                .andExpect(jsonPath("$.data.completionSummary.submitBlockingIssueCount").value(0))
                .andExpect(jsonPath("$.data.requiredMissingSections").isArray())
                .andExpect(jsonPath("$.data.optionalIncompleteSections").isArray())
                .andExpect(jsonPath("$.data.latestAnnouncedStageName").value("Stage 0"))
                .andExpect(jsonPath("$.data.latestResultStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.applicantId").doesNotExist())
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist())
                .andExpect(jsonPath("$.data.decidedBy").doesNotExist())
                .andExpect(jsonPath("$.data.correctedBy").doesNotExist())
                .andExpect(jsonPath("$.data.stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data.answerText").doesNotExist())
                .andExpect(jsonPath("$.data.nonServiceReason").doesNotExist())
                .andExpect(jsonPath("$.data.certificateNumber").doesNotExist())
                .andExpect(jsonPath("$.data.histories").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist());
    }

    @Test
    void get_application_dashboard_returns_required_missing_sections() throws Exception {
        Applicant applicant = createApplicant("api-dashboard-missing", "Api Dashboard Missing");
        Long jobPostingId = createPublishedJobPosting(new ApplicationFormConfigRequest(
                true,
                false,
                false,
                false,
                false,
                false,
                false
        ));
        Long applicationId = createApplication(applicant, jobPostingId);
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.submittable").value(false))
                .andExpect(jsonPath("$.data.completionSummary.requiredSectionCount").value(1))
                .andExpect(jsonPath("$.data.completionSummary.submitBlockingIssueCount").value(1))
                .andExpect(jsonPath("$.data.requiredMissingSections[0].sectionCode").value("EDUCATION"))
                .andExpect(jsonPath("$.data.requiredMissingSections[0].reasonCode").value("MISSING_ROW"));
    }

    @Test
    void get_application_dashboard_blocks_employee_admin_and_anonymous() throws Exception {
        Applicant applicant = createApplicant("api-dashboard-security", "Api Dashboard Security");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(employeeAuthentication("employee-dashboard", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        securedMockMvc.perform(get("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void get_application_dashboard_hides_other_applicants_application() throws Exception {
        Applicant owner = createApplicant("api-dashboard-owner", "Api Dashboard Owner");
        Applicant other = createApplicant("api-dashboard-other", "Api Dashboard Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());

        securedMockMvc().perform(get("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_application_form_page_returns_api_response() throws Exception {
        Applicant applicant = createApplicant("api-form-page", "Api Form Page");
        Long jobPostingId = createPublishedJobPosting(new ApplicationFormConfigRequest(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false
        ));
        Long applicationId = createApplication(applicant, jobPostingId);

        securedMockMvc().perform(get("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.jobPostingTitle").value("2026 recruitment"))
                .andExpect(jsonPath("$.data.jobPostingStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.postingType").value("PUBLIC_RECRUITMENT"))
                .andExpect(jsonPath("$.data.jobPositionId").isNumber())
                .andExpect(jsonPath("$.data.jobPositionName").value("Backend"))
                .andExpect(jsonPath("$.data.applicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.receptionStartDateTime").exists())
                .andExpect(jsonPath("$.data.receptionEndDateTime").exists())
                .andExpect(jsonPath("$.data.accepting").value(true))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.formConfig.useEducation").value(true))
                .andExpect(jsonPath("$.data.formConfig.requireEducation").value(true))
                .andExpect(jsonPath("$.data.formConfig.useMilitary").value(true))
                .andExpect(jsonPath("$.data.formConfig.requireMilitary").value(false))
                .andExpect(jsonPath("$.data.sections[0].sectionType").value("BASIC_INFO"))
                .andExpect(jsonPath("$.data.sections[0].label").value("Basic Info"))
                .andExpect(jsonPath("$.data.sections[0].enabled").value(true))
                .andExpect(jsonPath("$.data.sections[0].required").value(true))
                .andExpect(jsonPath("$.data.sections[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.sections[1].sectionType").value("MILITARY"))
                .andExpect(jsonPath("$.data.sections[1].required").value(false))
                .andExpect(jsonPath("$.data.sections[2].sectionType").value("EDUCATION"))
                .andExpect(jsonPath("$.data.sections[2].required").value(true))
                .andExpect(jsonPath("$.data.sections[0].pageNo").value(1))
                .andExpect(jsonPath("$.data.sections[0].pageTitle").value("Basic Info"))
                .andExpect(jsonPath("$.data.sections[1].pageNo").value(1))
                .andExpect(jsonPath("$.data.sections[2].pageNo").value(2))
                .andExpect(jsonPath("$.data.sections[2].pageTitle").value("Education And Career"))
                .andExpect(jsonPath("$.data.applicantId").doesNotExist())
                .andExpect(jsonPath("$.data.stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist())
                .andExpect(jsonPath("$.data.decidedBy").doesNotExist())
                .andExpect(jsonPath("$.data.correctedBy").doesNotExist())
                .andExpect(jsonPath("$.data.storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.storedFileName").doesNotExist());
    }

    @Test
    void get_application_form_page_blocks_employee_admin_and_anonymous() throws Exception {
        Applicant applicant = createApplicant("api-form-page-security", "Api Form Page Security");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(employeeAuthentication("employee-form-page", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        securedMockMvc.perform(get("/api/applications/{applicationId}/form-page", applicationId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void get_application_form_page_hides_other_applicants_application() throws Exception {
        Applicant owner = createApplicant("api-form-page-owner", "Api Form Page Owner");
        Applicant other = createApplicant("api-form-page-other", "Api Form Page Other");
        Long applicationId = createApplication(owner, createPublishedJobPosting());

        securedMockMvc().perform(get("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void get_my_applications_returns_empty_page_for_applicant_without_applications() throws Exception {
        Applicant applicant = createApplicant("api-my-list-empty", "Api My List Empty");

        securedMockMvc().perform(get("/api/applications/me")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void get_my_applications_blocks_employee_admin_and_anonymous() throws Exception {
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(get("/api/applications/me")
                        .with(authentication(employeeAuthentication("employee-admin", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        securedMockMvc.perform(get("/api/applications/me")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void unsupported_methods_are_not_added_for_my_applications() throws Exception {
        Applicant applicant = createApplicant("api-my-list-method", "Api My List Method");
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(post("/api/applications/me")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(put("/api/applications/me")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(patch("/api/applications/me")
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(delete("/api/applications/me")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupported_methods_are_not_added_for_application_dashboard() throws Exception {
        Applicant applicant = createApplicant("api-dashboard-method", "Api Dashboard Method");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(post("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(put("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(patch("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(delete("/api/applications/{applicationId}/dashboard", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupported_methods_are_not_added_for_application_form_page() throws Exception {
        Applicant applicant = createApplicant("api-form-page-method", "Api Form Page Method");
        Long applicationId = createApplication(applicant, createPublishedJobPosting());
        MockMvc securedMockMvc = securedMockMvc();

        securedMockMvc.perform(post("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(put("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(patch("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(applicant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        securedMockMvc.perform(delete("/api/applications/{applicationId}/form-page", applicationId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void put_method_is_not_supported_for_application_update() throws Exception {
        mockMvc.perform(put("/api/applications/{applicationId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_http_method_is_not_supported_for_application_delete() throws Exception {
        mockMvc.perform(delete("/api/applications/{applicationId}", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    private void authenticate(Applicant applicant) {
        authenticate(applicantAuthentication(applicant));
    }

    private void authenticate(Employee employee) {
        authenticate(CustomUserDetails.fromUser(employee, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    private void authenticate(CustomUserDetails userDetails) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    private void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication employeeAuthentication(String loginId, String authority) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                loginId,
                "Recruit",
                "Employee User",
                List.of(new SimpleGrantedAuthority(authority))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private MockMvc securedMockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
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
        return createPublishedJobPosting(new ApplicationFormConfigRequest(
                false,
                false,
                false,
                false,
                false,
                false,
                false
        ));
    }

    private Long createPublishedJobPosting(ApplicationFormConfigRequest formConfig) {
        Long jobPostingId = createDraftJobPosting(formConfig);
        jobPostingService.publish(jobPostingId);
        return jobPostingId;
    }

    private Long createDraftJobPosting(ApplicationFormConfigRequest formConfig) {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(
                        new JobPositionRequest("Backend", 0),
                        new JobPositionRequest("Frontend", 1)
                ),
                formConfig
        ));
    }

    private void createQuestion(
            Long jobPostingId,
            boolean required,
            QuestionAnswerType answerType,
            int maxLength
    ) {
        jobPostingQuestionService.createQuestion(jobPostingId, new JobPostingQuestionCreateRequest(
                null,
                "Submit question",
                "Submit helper",
                QuestionCategory.JOB_SPECIFIC,
                answerType,
                required,
                null,
                maxLength,
                0
        ));
    }

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Stage " + stageOrder,
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, stageOrder + 1, 10, 0),
                finalStage
        ));
    }

    private void decideAndAnnounce(Long jobPostingId, Long stageId, StageResultStatus status) {
        stageService.start(jobPostingId, stageId);
        stageResultService.initialize(stageId);
        Long resultId = stageResultService.getResults(stageId).get(0).stageResultId();
        stageResultService.updateResult(stageId, resultId, new StageResultUpdateRequest(status, null, "internal"), "employee01");
        stageService.announce(jobPostingId, stageId);
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
