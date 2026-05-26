package com.shinyoung.recruit.service;

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
import com.shinyoung.recruit.dto.response.InterviewerInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewerInterviewServiceTest {

    @Autowired
    private InterviewerInterviewService interviewerInterviewService;

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

    @Test
    void getMyInterviews_returnsOnlyVisibleAssignedInterviewerRowsForCurrentEmployee() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        Employee otherEmployee = saveEmployee();
        JobApplication application = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview cancelled = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Group C", start().plusHours(4), InterviewStatus.DRAFT);
        Interview otherEmployeeInterview = saveInterview(jobPosting, stage, "Group D", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Group E", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview candidateOnlyInterview = saveInterview(jobPosting, stage, "Group F", start().plusHours(10), InterviewStatus.CONFIRMED);

        saveInterviewer(confirmed, employee, 1);
        saveCandidate(confirmed, application, 1);
        saveInterviewer(cancelled, employee, 1);
        saveInterviewer(draft, employee, 1);
        saveInterviewer(otherEmployeeInterview, otherEmployee, 1);
        InterviewParticipant cancelledParticipant = InterviewParticipant.interviewer(cancelledParticipantInterview, employee, 1);
        cancelledParticipant.cancel();
        participantRepository.saveAndFlush(cancelledParticipant);
        saveCandidate(candidateOnlyInterview, application, 1);

        List<InterviewerInterviewSummaryResponse> responses = interviewerInterviewService.getMyInterviews(
                employee.getId(),
                null,
                null,
                null
        );

        assertThat(responses).extracting(InterviewerInterviewSummaryResponse::interviewId)
                .containsExactly(confirmed.getId(), cancelled.getId());
        assertThat(responses).extracting(InterviewerInterviewSummaryResponse::status)
                .containsExactly(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED);
        assertThat(responses.get(0).candidateCount()).isEqualTo(1);
    }

    @Test
    void getMyInterviews_filtersStatusAndTimeRangeAndRejectsInvalidRequest() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        Interview early = saveInterview(jobPosting, stage, "Early", start(), InterviewStatus.CONFIRMED);
        Interview late = saveInterview(jobPosting, stage, "Late", start().plusHours(3), InterviewStatus.CANCELLED);
        saveInterviewer(early, employee, 1);
        saveInterviewer(late, employee, 1);

        assertThat(interviewerInterviewService.getMyInterviews(
                employee.getId(),
                InterviewStatus.CONFIRMED,
                null,
                null
        )).extracting(InterviewerInterviewSummaryResponse::interviewId)
                .containsExactly(early.getId());

        assertThat(interviewerInterviewService.getMyInterviews(
                employee.getId(),
                null,
                start().plusHours(2),
                start().plusHours(5)
        )).extracting(InterviewerInterviewSummaryResponse::interviewId)
                .containsExactly(late.getId());

        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviews(
                employee.getId(),
                InterviewStatus.DRAFT,
                null,
                null
        )).isInstanceOf(InvalidInterviewException.class);
        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviews(
                employee.getId(),
                null,
                start().plusHours(2),
                start().plusHours(2)
        )).isInstanceOf(InvalidInterviewException.class);
    }

    @Test
    void getMyInterviewDetail_returnsAssignedCandidatesAndHidesNonVisibleInterviews() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Employee employee = saveEmployee();
        Employee otherEmployee = saveEmployee();
        JobApplication firstCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        JobApplication secondCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        JobApplication cancelledCandidate = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview cancelled = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Group C", start().plusHours(4), InterviewStatus.DRAFT);
        Interview other = saveInterview(jobPosting, stage, "Group D", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Group E", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview candidateOnlyInterview = saveInterview(jobPosting, stage, "Group F", start().plusHours(10), InterviewStatus.CONFIRMED);
        saveInterviewer(confirmed, employee, 1);
        saveCandidate(confirmed, firstCandidate, 1);
        saveCandidate(confirmed, secondCandidate, 2);
        InterviewParticipant cancelledCandidateParticipant = InterviewParticipant.candidate(confirmed, cancelledCandidate, 3);
        cancelledCandidateParticipant.cancel();
        participantRepository.saveAndFlush(cancelledCandidateParticipant);
        saveInterviewer(cancelled, employee, 1);
        saveInterviewer(draft, employee, 1);
        saveInterviewer(other, otherEmployee, 1);
        InterviewParticipant cancelledInterviewer = InterviewParticipant.interviewer(cancelledParticipantInterview, employee, 1);
        cancelledInterviewer.cancel();
        participantRepository.saveAndFlush(cancelledInterviewer);
        saveCandidate(candidateOnlyInterview, firstCandidate, 1);

        InterviewerInterviewDetailResponse confirmedResponse = interviewerInterviewService.getMyInterviewDetail(
                employee.getId(),
                confirmed.getId()
        );
        InterviewerInterviewDetailResponse cancelledResponse = interviewerInterviewService.getMyInterviewDetail(
                employee.getId(),
                cancelled.getId()
        );

        assertThat(confirmedResponse.status()).isEqualTo(InterviewStatus.CONFIRMED);
        assertThat(confirmedResponse.candidates()).extracting(candidate -> candidate.jobApplicationId())
                .containsExactly(firstCandidate.getId(), secondCandidate.getId());
        assertThat(cancelledResponse.status()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(cancelledResponse.cancelled()).isTrue();
        assertThat(cancelledResponse.guideMessage()).isNotBlank();
        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviewDetail(employee.getId(), draft.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviewDetail(employee.getId(), other.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviewDetail(employee.getId(), cancelledParticipantInterview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> interviewerInterviewService.getMyInterviewDetail(employee.getId(), candidateOnlyInterview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
