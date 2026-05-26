package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewCandidateParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewInterviewerParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewParticipantReplaceRequest;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import com.shinyoung.recruit.service.InterviewService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewAdminControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private StageResultRepository stageResultRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(authentication(employeeAuthentication())))
                .build();
    }

    @Test
    void create_and_list_admin_interview_returns_api_response() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage interviewStage = saveStage(jobPosting, StageType.FIRST_INTERVIEW, 1);

        mockMvc.perform(post("/admin/job-postings/{jobPostingId}/interviews", jobPosting.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stageId": %d,
                                  "groupName": "Group A",
                                  "startDateTime": "2026-06-01T10:00:00",
                                  "endDateTime": "2026-06-01T11:00:00",
                                  "method": "IN_PERSON",
                                  "locationName": "Head office",
                                  "roomName": "Room 1",
                                  "memo": "memo"
                                }
                                """.formatted(interviewStage.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/interviews", jobPosting.getId())
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].stageId").value(interviewStage.getId()))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"));
    }

    @Test
    void detail_confirm_and_cancel_return_api_response() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage documentStage = saveStage(jobPosting, StageType.DOCUMENT, 1);
        documentStage.announce();
        Stage interviewStage = saveStage(jobPosting, StageType.FIRST_INTERVIEW, 2);
        JobApplication application = saveSubmittedApplication(jobPosting);
        saveStageResult(documentStage, application, StageResultStatus.PASSED);
        Employee employee = saveEmployee();
        Long interviewId = interviewService.createDraft(
                jobPosting.getId(),
                createRequest(interviewStage.getId(), start())
        );
        interviewService.replaceParticipants(
                interviewId,
                new InterviewParticipantReplaceRequest(
                        List.of(new InterviewCandidateParticipantRequest(application.getId(), 1)),
                        List.of(new InterviewInterviewerParticipantRequest(employee.getId(), 1))
                )
        );

        mockMvc.perform(get("/admin/interviews/{interviewId}", interviewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidates[0].jobApplicationId").value(application.getId()))
                .andExpect(jsonPath("$.data.interviewers[0].employeeId").value(employee.getId()));

        mockMvc.perform(post("/admin/interviews/{interviewId}/confirm", interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/admin/interviews/{interviewId}/cancel", interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminInterviewApisRequireAdminAuthentication() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Applicant applicant = saveApplicant();

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/interviews", jobPosting.getId())
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/interviews", jobPosting.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/admin/job-postings/{jobPostingId}/interviews", jobPosting.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private InterviewCreateRequest createRequest(Long stageId, LocalDateTime startDateTime) {
        return new InterviewCreateRequest(
                stageId,
                "Group A",
                startDateTime,
                startDateTime.plusHours(1),
                InterviewMethod.IN_PERSON,
                "Head office",
                "Room 1",
                null,
                null
        );
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting",
                "Content",
                start().minusDays(1),
                start().plusDays(10)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1, 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(JobPosting jobPosting, StageType stageType, int stageOrder) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, stageType.name(), stageType, stageOrder, null, false)
        );
    }

    private JobApplication saveSubmittedApplication(JobPosting jobPosting) {
        Applicant applicant = saveApplicant();
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                applicant.getName(),
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(start().minusHours(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private StageResult saveStageResult(Stage stage, JobApplication application, StageResultStatus status) {
        StageResult result = StageResult.initialize(stage, application);
        result.updateResult(status, BigDecimal.valueOf(90), "pass", start().minusMinutes(30), "admin");
        return stageResultRepository.saveAndFlush(result);
    }

    private Applicant saveApplicant() {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("Applicant");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("Applicant");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Employee saveEmployee() {
        Employee employee = new Employee();
        employee.setLoginId("employee-" + UUID.randomUUID());
        employee.setName("Interviewer");
        employee.setDeptName("HR-" + UUID.randomUUID());
        return employeeRepository.saveAndFlush(employee);
    }

    private Authentication employeeAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin01",
                "Recruit",
                "Admin User",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
