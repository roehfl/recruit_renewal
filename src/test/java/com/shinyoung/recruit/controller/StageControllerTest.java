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
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.service.JobPostingService;
import com.shinyoung.recruit.service.StageResultService;
import com.shinyoung.recruit.service.StageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class StageControllerTest {

    @Autowired
    private WebApplicationContext context;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(authentication(employeeAuthentication("employee01", "ROLE_ADMIN"))))
                .build();
    }

    @Test
    void get_stages_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long firstStageId = createStage(jobPostingId, 0, false);
        Long secondStageId = createStage(jobPostingId, 1, true);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages", jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].id").value(firstStageId))
                .andExpect(jsonPath("$.data[1].id").value(secondStageId));
    }

    @Test
    void get_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.id").value(stageId))
                .andExpect(jsonPath("$.data.jobPostingId").value(jobPostingId))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void create_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStageJson(0, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void update_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, stageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateStageJson(1, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void reorder_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long firstStageId = createStage(jobPostingId, 0, false);
        Long secondStageId = createStage(jobPostingId, 1, true);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/reorder", jobPostingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "stageId": %d,
                                      "stageOrder": 1
                                    },
                                    {
                                      "stageId": %d,
                                      "stageOrder": 0
                                    }
                                  ]
                                }
                                """.formatted(firstStageId, secondStageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data[0].id").value(secondStageId))
                .andExpect(jsonPath("$.data[0].stageOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(firstStageId))
                .andExpect(jsonPath("$.data[1].stageOrder").value(1));
    }

    @Test
    void start_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/start", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void announce_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "controller-announce");

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/announce", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void close_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);
        stageService.start(jobPostingId, stageId);
        prepareDecidedStageResult(jobPostingId, stageId, "controller-close");
        stageService.announce(jobPostingId, stageId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/close", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void delete_stage_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/delete", jobPostingId, stageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").value(stageId));
    }

    @Test
    void invalid_create_request_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/1/stages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stageName": " ",
                                  "stageType": "DOCUMENT",
                                  "stageOrder": 0,
                                  "resultAnnouncementDateTime": "2026-07-01T10:00:00",
                                  "finalStage": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_reorder_request_returns_api_response() throws Exception {
        mockMvc.perform(post("/admin/job-postings/1/stages/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "stageId": null,
                                      "stageOrder": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void stage_not_found_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages/{stageId}", jobPostingId, 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalid_status_command_returns_api_response() throws Exception {
        Long jobPostingId = createJobPosting();
        Long stageId = createStage(jobPostingId, 0, false);
        jobPostingService.publish(jobPostingId);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/stages/{stageId}/announce", jobPostingId, stageId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void put_method_is_not_supported_for_stage_update() throws Exception {
        mockMvc.perform(put("/admin/job-postings/{jobPostingId}/stages/{stageId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_http_method_is_not_supported_for_stage_delete() throws Exception {
        mockMvc.perform(delete("/admin/job-postings/{jobPostingId}/stages/{stageId}", 1L, 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void admin_stage_api_blocks_applicant_employee_without_admin_authority_and_anonymous() throws Exception {
        Long jobPostingId = createJobPosting();
        Applicant applicant = createApplicant("stage-auth-applicant");

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages", jobPostingId)
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages", jobPostingId)
                        .with(authentication(employeeAuthentication("employee02", "ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access is denied."));

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/stages", jobPostingId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    private Long createJobPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)
        ));
    }

    private void prepareDecidedStageResult(Long jobPostingId, Long stageId, String suffix) {
        createSubmittedApplication("stage-" + suffix, jobPostingId);
        stageResultService.initialize(stageId);
        for (AdminStageResultResponse result : stageResultService.getResults(stageId)) {
            stageResultService.updateResult(
                    stageId,
                    result.stageResultId(),
                    new StageResultUpdateRequest(StageResultStatus.PASSED, null, null),
                    "employee01"
            );
        }
    }

    private Long createSubmittedApplication(String loginId, Long jobPostingId) {
        Applicant applicant = createApplicant(loginId);
        Long applicationId = jobApplicationService.create(
                applicant.getId(),
                new ApplicationCreateRequest(jobPostingId, firstJobPositionId(jobPostingId))
        );
        jobApplicationService.submit(applicant.getId(), applicationId);
        return applicationId;
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

    private Long createStage(Long jobPostingId, int stageOrder, boolean finalStage) {
        return stageService.create(jobPostingId, new StageCreateRequest(
                "Document screening",
                StageType.DOCUMENT,
                stageOrder,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                finalStage
        ));
    }

    private String createStageJson(int stageOrder, boolean finalStage) {
        return """
                {
                  "stageName": "Document screening",
                  "stageType": "DOCUMENT",
                  "stageOrder": %d,
                  "resultAnnouncementDateTime": "2026-07-01T10:00:00",
                  "finalStage": %s
                }
                """.formatted(stageOrder, finalStage);
    }

    private String updateStageJson(int stageOrder, boolean finalStage) {
        return """
                {
                  "stageName": "First interview",
                  "stageType": "FIRST_INTERVIEW",
                  "stageOrder": %d,
                  "resultAnnouncementDateTime": "2026-07-02T10:00:00",
                  "finalStage": %s
                }
                """.formatted(stageOrder, finalStage);
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

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))
        );
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
