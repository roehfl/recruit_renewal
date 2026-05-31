package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.service.InterviewEvaluationAdminService;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewEvaluationAdminControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository participantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private InterviewEvaluationRepository evaluationRepository;

    @Autowired
    private InterviewEvaluationAdminService evaluationAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void initialize_createsEvaluationsForAdmin() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        saveInterviewer(interview, saveEmployee(), 2);

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/initialize", interview.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewId").value(interview.getId()))
                .andExpect(jsonPath("$.data.createdCount").value(2))
                .andExpect(jsonPath("$.data.alreadyExistedCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void initialize_rejectsNonConfirmedInterview() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview draft = saveInterview(jobPosting, stage, InterviewStatus.DRAFT);

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/initialize", draft.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void initialize_blocksApplicantAndAnonymous() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/initialize", interview.getId())
                        .with(authentication(applicantAuthentication(saveApplicant()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/initialize", interview.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getInterviewEvaluations_returnsCandidateGroupedSummary() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        saveInterviewer(interview, saveEmployee(), 2);
        evaluationAdminService.initialize(interview.getId());
        List<InterviewEvaluation> evaluations = evaluationRepository.findByInterviewId(interview.getId());
        submit(evaluations.get(0), EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);

        mockMvc.perform(get("/api/admin/interviews/{interviewId}/evaluations", interview.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewId").value(interview.getId()))
                .andExpect(jsonPath("$.data.candidates[0].evaluations.length()").value(2))
                .andExpect(jsonPath("$.data.candidates[0].summary.submittedCount").value(1))
                .andExpect(jsonPath("$.data.candidates[0].summary.totalEvaluatorCount").value(2))
                .andExpect(jsonPath("$.data.candidates[0].summary.gradeDistribution.vg").value(1))
                .andExpect(jsonPath("$.data.candidates[0].summary.recommendationDistribution.strongYes").value(1))
                .andExpect(jsonPath("$.data.candidates[0].evaluations[0].interviewerName").exists());
    }

    @Test
    void getStageEvaluations_returnsArrayPerInterview() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        evaluationAdminService.initialize(interview.getId());

        mockMvc.perform(get("/api/admin/stages/{stageId}/interview-evaluations", stage.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].interviewId").value(interview.getId()))
                .andExpect(jsonPath("$.data[0].candidates[0].evaluations[0].evaluationId").exists());
    }

    @Test
    void getApplicationEvaluations_returnsArrayPerInterviewAndBlocksApplicant() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        JobApplication application = saveSubmittedApplication(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, application, 1);
        saveInterviewer(interview, saveEmployee(), 1);
        evaluationAdminService.initialize(interview.getId());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/interview-evaluations", application.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].interviewId").value(interview.getId()))
                .andExpect(jsonPath("$.data[0].evaluations[0].evaluationId").exists());

        mockMvc.perform(get("/api/admin/applications/{applicationId}/interview-evaluations", application.getId())
                        .with(authentication(applicantAuthentication(saveApplicant()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reopen_transitionsSubmittedEvaluationBackToDraft() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        evaluationAdminService.initialize(interview.getId());
        InterviewEvaluation evaluation = evaluationRepository.findByInterviewId(interview.getId()).get(0);
        submit(evaluation, EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen",
                        interview.getId(), evaluation.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.grade").value("VG"))
                .andExpect(jsonPath("$.data.interviewerName").exists());
    }

    @Test
    void reopen_rejectsDraftAndBlocksApplicant() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        evaluationAdminService.initialize(interview.getId());
        InterviewEvaluation draft = evaluationRepository.findByInterviewId(interview.getId()).get(0);

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen",
                        interview.getId(), draft.getId())
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/admin/interviews/{interviewId}/evaluations/{evaluationId}/reopen",
                        interview.getId(), draft.getId())
                        .with(authentication(applicantAuthentication(saveApplicant()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void submit(InterviewEvaluation evaluation, EvaluationGrade grade, EvaluationRecommendation recommendation) {
        evaluation.updateContent(grade, recommendation, "comment");
        evaluation.submit(start());
        evaluationRepository.saveAndFlush(evaluation);
    }

    private InterviewParticipant saveInterviewer(Interview interview, Employee employee, int sortOrder) {
        return participantRepository.saveAndFlush(InterviewParticipant.interviewer(interview, employee, sortOrder));
    }

    private InterviewParticipant saveCandidate(Interview interview, JobApplication application, int sortOrder) {
        return participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, sortOrder));
    }

    private Interview saveInterview(JobPosting jobPosting, Stage stage, InterviewStatus status) {
        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                "Group A",
                start(),
                start().plusHours(1),
                InterviewMethod.IN_PERSON,
                "Head office",
                "Room 1",
                null,
                "admin memo"
        );
        if (status == InterviewStatus.CONFIRMED) {
            interview.confirm();
        }
        if (status == InterviewStatus.CANCELLED) {
            interview.confirm();
            interview.cancel();
        }
        return interviewRepository.saveAndFlush(interview);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create(
                "Posting",
                "Content",
                start().minusDays(1),
                start().plusDays(10)
        );
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(JobPosting jobPosting) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, "First interview", StageType.FIRST_INTERVIEW, 1, null, false)
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

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "admin-" + UUID.randomUUID(),
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
