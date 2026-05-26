package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewerInterviewControllerTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void getMyInterviews_returnsOnlyInterviewerSafeFields() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        JobApplication application = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview draft = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.DRAFT);
        saveInterviewer(confirmed, employee, 1);
        saveCandidate(confirmed, application, 1);
        saveInterviewer(draft, employee, 1);

        mockMvc.perform(get("/interviewer/interviews")
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].interviewId").value(confirmed.getId()))
                .andExpect(jsonPath("$.data[0].candidateCount").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data[0].interviewers").doesNotExist())
                .andExpect(jsonPath("$.data[0].employeeId").doesNotExist())
                .andExpect(jsonPath("$.data[0].stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data[0].resultStatus").doesNotExist())
                .andExpect(jsonPath("$.data[1]").doesNotExist());
    }

    @Test
    void getMyInterviewDetail_returnsAssignedCandidatesAndHidesInternalFields() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        JobApplication firstCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        JobApplication secondCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        JobApplication cancelledCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview cancelled = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CANCELLED);
        saveInterviewer(cancelled, employee, 1);
        saveCandidate(cancelled, firstCandidate, 1);
        saveCandidate(cancelled, secondCandidate, 2);
        InterviewParticipant cancelledCandidateParticipant = InterviewParticipant.candidate(cancelled, cancelledCandidate, 3);
        cancelledCandidateParticipant.cancel();
        participantRepository.saveAndFlush(cancelledCandidateParticipant);

        mockMvc.perform(get("/interviewer/interviews/{interviewId}", cancelled.getId())
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.interviewId").value(cancelled.getId()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.guideMessage").exists())
                .andExpect(jsonPath("$.data.candidates[0].jobApplicationId").value(firstCandidate.getId()))
                .andExpect(jsonPath("$.data.candidates[0].applicantId").value(firstCandidate.getApplicant().getId()))
                .andExpect(jsonPath("$.data.candidates[1].jobApplicationId").value(secondCandidate.getId()))
                .andExpect(jsonPath("$.data.candidates[2]").doesNotExist())
                .andExpect(jsonPath("$.data.memo").doesNotExist())
                .andExpect(jsonPath("$.data.interviewers").doesNotExist())
                .andExpect(jsonPath("$.data.employeeId").doesNotExist())
                .andExpect(jsonPath("$.data.stageResultId").doesNotExist())
                .andExpect(jsonPath("$.data.histories").doesNotExist());
    }

    @Test
    void invalidStatusDateRangeAndNonOwnedDetailReturnExpectedErrors() throws Exception {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        Employee otherEmployee = saveEmployee();
        Interview draft = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.DRAFT);
        Interview other = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.CONFIRMED);
        saveInterviewer(draft, employee, 1);
        saveInterviewer(other, otherEmployee, 1);

        mockMvc.perform(get("/interviewer/interviews")
                        .param("status", "DRAFT")
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/interviewer/interviews")
                        .param("from", "2026-06-01T10:00:00")
                        .param("to", "2026-06-01T10:00:00")
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/interviewer/interviews/{interviewId}", draft.getId())
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/interviewer/interviews/{interviewId}", other.getId())
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void interviewerInterviewApisBlockApplicantAndAnonymous() throws Exception {
        Employee employee = saveEmployee();
        Applicant applicant = saveApplicant();

        mockMvc.perform(get("/interviewer/interviews")
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/interviewer/interviews")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/interviewer/interviews")
                        .with(authentication(employeeAuthentication(employee))))
                .andExpect(status().isOk());
    }

    private InterviewParticipant saveInterviewer(Interview interview, Employee employee, int sortOrder) {
        return participantRepository.saveAndFlush(InterviewParticipant.interviewer(interview, employee, sortOrder));
    }

    private InterviewParticipant saveCandidate(Interview interview, JobApplication application, int sortOrder) {
        return participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, sortOrder));
    }

    private Interview saveInterview(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            LocalDateTime startDateTime,
            InterviewStatus status
    ) {
        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                groupName,
                startDateTime,
                startDateTime.plusHours(1),
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
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1, 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(JobPosting jobPosting) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, "First interview", StageType.FIRST_INTERVIEW, 1, null, false)
        );
    }

    private JobApplication saveSubmittedApplication(Applicant applicant, JobPosting jobPosting) {
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
