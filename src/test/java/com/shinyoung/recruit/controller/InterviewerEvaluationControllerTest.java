package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
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
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
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
class InterviewerEvaluationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InterviewEvaluationAdminService adminService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository participantRepository;

    @Autowired
    private InterviewEvaluationRepository evaluationRepository;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getMyEvaluations_returnsOnlyOwnEvaluations() throws Exception {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();

        mockMvc.perform(get("/api/interviewer/interviews/{interviewId}/evaluations", fixture.interview.getId())
                        .with(authentication(employeeAuthentication(fixture.interviewerA.getEmployee()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewId").value(fixture.interview.getId()))
                .andExpect(jsonPath("$.data.interviewStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.evaluations[0].candidateName").exists())
                .andExpect(jsonPath("$.data.evaluations[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.evaluations[1]").doesNotExist());
    }

    @Test
    void saveThenSubmit_persistsGradeAndStatus() throws Exception {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);

        mockMvc.perform(post("/api/interviewer/interviews/{interviewId}/evaluations/{evaluationId}",
                        fixture.interview.getId(), evaluationId)
                        .with(authentication(employeeAuthentication(fixture.interviewerA.getEmployee())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade":"G_PLUS","recommendation":"YES","comment":"solid"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.grade").value("G_PLUS"))
                .andExpect(jsonPath("$.data.comment").value("solid"));

        mockMvc.perform(post("/api/interviewer/interviews/{interviewId}/evaluations/{evaluationId}/submit",
                        fixture.interview.getId(), evaluationId)
                        .with(authentication(employeeAuthentication(fixture.interviewerA.getEmployee())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grade":"VG","recommendation":"STRONG_YES","comment":"excellent"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.grade").value("VG"))
                .andExpect(jsonPath("$.data.submittedAt").exists());
    }

    @Test
    void getOtherInterviewerEvaluation_returnsNotFound() throws Exception {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationOfB = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerB);

        mockMvc.perform(get("/api/interviewer/interviews/{interviewId}/evaluations/{evaluationId}",
                        fixture.interview.getId(), evaluationOfB)
                        .with(authentication(employeeAuthentication(fixture.interviewerA.getEmployee()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void evaluationApisBlockApplicantAndAnonymous() throws Exception {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();

        mockMvc.perform(get("/api/interviewer/interviews/{interviewId}/evaluations", fixture.interview.getId())
                        .with(authentication(applicantAuthentication(saveApplicant()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/interviewer/interviews/{interviewId}/evaluations", fixture.interview.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long ownedEvaluationId(Long interviewId, InterviewParticipant interviewer) {
        List<InterviewEvaluation> evaluations = evaluationRepository
                .findByInterviewIdAndInterviewerParticipantId(interviewId, interviewer.getId());
        return evaluations.get(0).getId();
    }

    private Fixture confirmedInterviewWithTwoInterviewers() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        InterviewParticipant interviewerA = saveInterviewer(interview, saveEmployee(), 1);
        InterviewParticipant interviewerB = saveInterviewer(interview, saveEmployee(), 2);
        adminService.initialize(interview.getId());
        return new Fixture(interview, interviewerA, interviewerB);
    }

    private record Fixture(Interview interview, InterviewParticipant interviewerA, InterviewParticipant interviewerB) {
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

    private Authentication employeeAuthentication(Employee employee) {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                employee.getLoginId(),
                employee.getDeptName(),
                employee.getName(),
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
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
