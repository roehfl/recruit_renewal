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
import com.shinyoung.recruit.dto.response.ApplicantInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
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
class ApplicantInterviewServiceTest {

    @Autowired
    private ApplicantInterviewService applicantInterviewService;

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
    void getMyInterviews_returnsOnlyVisibleAssignedCandidateRowsForCurrentApplicant() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Applicant applicant = saveApplicant();
        JobApplication application = saveSubmittedApplication(applicant, jobPosting);
        JobApplication otherApplication = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview cancelled = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Group C", start().plusHours(4), InterviewStatus.DRAFT);
        Interview otherApplicantInterview = saveInterview(jobPosting, stage, "Group D", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Group E", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview interviewerOnlyInterview = saveInterview(jobPosting, stage, "Group F", start().plusHours(10), InterviewStatus.CONFIRMED);

        saveCandidate(confirmed, application, 1);
        saveCandidate(cancelled, application, 1);
        saveCandidate(draft, application, 1);
        saveCandidate(otherApplicantInterview, otherApplication, 1);
        InterviewParticipant cancelledParticipant = InterviewParticipant.candidate(cancelledParticipantInterview, application, 1);
        cancelledParticipant.cancel();
        participantRepository.saveAndFlush(cancelledParticipant);
        participantRepository.saveAndFlush(InterviewParticipant.interviewer(interviewerOnlyInterview, saveEmployee(), 1));

        List<ApplicantInterviewSummaryResponse> responses = applicantInterviewService.getMyInterviews(
                applicant.getId(),
                null,
                null,
                null
        );

        assertThat(responses).extracting(ApplicantInterviewSummaryResponse::interviewId)
                .containsExactly(confirmed.getId(), cancelled.getId());
        assertThat(responses).extracting(ApplicantInterviewSummaryResponse::status)
                .containsExactly(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED);
    }

    @Test
    void getMyInterviews_filtersStatusAndTimeRangeAndRejectsInvalidRequest() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Applicant applicant = saveApplicant();
        JobApplication application = saveSubmittedApplication(applicant, jobPosting);
        Interview early = saveInterview(jobPosting, stage, "Early", start(), InterviewStatus.CONFIRMED);
        Interview late = saveInterview(jobPosting, stage, "Late", start().plusHours(3), InterviewStatus.CANCELLED);
        saveCandidate(early, application, 1);
        saveCandidate(late, application, 1);

        assertThat(applicantInterviewService.getMyInterviews(
                applicant.getId(),
                InterviewStatus.CONFIRMED,
                null,
                null
        )).extracting(ApplicantInterviewSummaryResponse::interviewId)
                .containsExactly(early.getId());

        assertThat(applicantInterviewService.getMyInterviews(
                applicant.getId(),
                null,
                start().plusHours(2),
                start().plusHours(5)
        )).extracting(ApplicantInterviewSummaryResponse::interviewId)
                .containsExactly(late.getId());

        assertThatThrownBy(() -> applicantInterviewService.getMyInterviews(
                applicant.getId(),
                InterviewStatus.DRAFT,
                null,
                null
        )).isInstanceOf(InvalidInterviewException.class);
        assertThatThrownBy(() -> applicantInterviewService.getMyInterviews(
                applicant.getId(),
                null,
                start().plusHours(2),
                start().plusHours(2)
        )).isInstanceOf(InvalidInterviewException.class);
    }

    @Test
    void getMyApplicationInterviews_validatesApplicationOwnershipAndWithdrawnState() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Applicant applicant = saveApplicant();
        Applicant otherApplicant = saveApplicant();
        JobApplication application = saveSubmittedApplication(applicant, jobPosting);
        JobApplication otherApplication = saveSubmittedApplication(otherApplicant, jobPosting);
        JobApplication withdrawnApplication = saveSubmittedApplication(saveApplicant(), jobPosting);
        withdrawnApplication.withdraw(start().minusMinutes(10));
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview draft = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.DRAFT);
        saveCandidate(confirmed, application, 1);
        saveCandidate(draft, application, 1);

        assertThat(applicantInterviewService.getMyApplicationInterviews(
                applicant.getId(),
                application.getId(),
                null,
                null,
                null
        )).extracting(ApplicantInterviewSummaryResponse::interviewId)
                .containsExactly(confirmed.getId());

        assertThatThrownBy(() -> applicantInterviewService.getMyApplicationInterviews(
                applicant.getId(),
                otherApplication.getId(),
                null,
                null,
                null
        )).isInstanceOf(JobApplicationNotFoundException.class);
        assertThatThrownBy(() -> applicantInterviewService.getMyApplicationInterviews(
                withdrawnApplication.getApplicant().getId(),
                withdrawnApplication.getId(),
                null,
                null,
                null
        )).isInstanceOf(InvalidInterviewException.class);
    }

    @Test
    void getMyInterviewDetail_returnsVisibleDetailAndHidesNonVisibleInterviews() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Applicant applicant = saveApplicant();
        JobApplication application = saveSubmittedApplication(applicant, jobPosting);
        JobApplication otherApplication = saveSubmittedApplication(saveApplicant(), jobPosting);
        Interview confirmed = saveInterview(jobPosting, stage, "Group A", start(), InterviewStatus.CONFIRMED);
        Interview cancelled = saveInterview(jobPosting, stage, "Group B", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Group C", start().plusHours(4), InterviewStatus.DRAFT);
        Interview other = saveInterview(jobPosting, stage, "Group D", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Group E", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview interviewerOnlyInterview = saveInterview(jobPosting, stage, "Group F", start().plusHours(10), InterviewStatus.CONFIRMED);
        saveCandidate(confirmed, application, 1);
        saveCandidate(cancelled, application, 1);
        saveCandidate(draft, application, 1);
        saveCandidate(other, otherApplication, 1);
        InterviewParticipant cancelledParticipant = InterviewParticipant.candidate(cancelledParticipantInterview, application, 1);
        cancelledParticipant.cancel();
        participantRepository.saveAndFlush(cancelledParticipant);
        participantRepository.saveAndFlush(InterviewParticipant.interviewer(interviewerOnlyInterview, saveEmployee(), 1));

        ApplicantInterviewDetailResponse confirmedResponse = applicantInterviewService.getMyInterviewDetail(
                applicant.getId(),
                confirmed.getId()
        );
        ApplicantInterviewDetailResponse cancelledResponse = applicantInterviewService.getMyInterviewDetail(
                applicant.getId(),
                cancelled.getId()
        );

        assertThat(confirmedResponse.status()).isEqualTo(InterviewStatus.CONFIRMED);
        assertThat(confirmedResponse.guideMessage()).isNull();
        assertThat(cancelledResponse.status()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(cancelledResponse.cancelled()).isTrue();
        assertThat(cancelledResponse.guideMessage()).isNotBlank();
        assertThatThrownBy(() -> applicantInterviewService.getMyInterviewDetail(applicant.getId(), draft.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> applicantInterviewService.getMyInterviewDetail(applicant.getId(), other.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> applicantInterviewService.getMyInterviewDetail(applicant.getId(), cancelledParticipantInterview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> applicantInterviewService.getMyInterviewDetail(applicant.getId(), interviewerOnlyInterview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
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
