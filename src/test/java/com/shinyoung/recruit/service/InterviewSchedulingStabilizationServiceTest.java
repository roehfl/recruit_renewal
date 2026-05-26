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
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewCandidateParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewInterviewerParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewParticipantReplaceRequest;
import com.shinyoung.recruit.dto.response.ApplicantInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewDetailResponse;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewSchedulingStabilizationServiceTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private ApplicantInterviewService applicantInterviewService;

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
    private StageResultRepository stageResultRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void cancelledInterviewRemainsVisibleWithoutChangingParticipantsOrStageResult() {
        JobPosting jobPosting = saveJobPosting();
        Stage documentStage = saveStage(jobPosting, StageType.DOCUMENT, 1);
        documentStage.announce();
        Stage interviewStage = saveStage(jobPosting, StageType.FIRST_INTERVIEW, 2);
        Applicant applicant = saveApplicant();
        JobApplication application = saveSubmittedApplication(applicant, jobPosting);
        StageResult previousResult = saveStageResult(documentStage, application, StageResultStatus.PASSED);
        Employee interviewer = saveEmployee();
        Long interviewId = interviewService.createDraft(
                jobPosting.getId(),
                createRequest(interviewStage.getId(), start())
        );
        interviewService.replaceParticipants(
                interviewId,
                new InterviewParticipantReplaceRequest(
                        List.of(new InterviewCandidateParticipantRequest(application.getId(), 1)),
                        List.of(new InterviewInterviewerParticipantRequest(interviewer.getId(), 1))
                )
        );
        interviewService.confirm(interviewId);

        interviewService.cancel(interviewId);

        Interview cancelledInterview = interviewRepository.findById(interviewId).orElseThrow();
        List<InterviewParticipant> participants = participantRepository.findByInterviewIdForAdminDetail(interviewId);
        StageResult reloadedResult = stageResultRepository.findById(previousResult.getId()).orElseThrow();
        ApplicantInterviewDetailResponse applicantResponse = applicantInterviewService.getMyInterviewDetail(
                applicant.getId(),
                interviewId
        );
        InterviewerInterviewDetailResponse interviewerResponse = interviewerInterviewService.getMyInterviewDetail(
                interviewer.getId(),
                interviewId
        );

        assertThat(cancelledInterview.getStatus()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(participants).hasSize(2);
        assertThat(participants).allMatch(participant ->
                participant.getParticipantStatus() == InterviewParticipantStatus.ASSIGNED
        );
        assertThat(reloadedResult.getResultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(applicantResponse.status()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(applicantResponse.cancelled()).isTrue();
        assertThat(applicantResponse.guideMessage()).isNotBlank();
        assertThat(interviewerResponse.status()).isEqualTo(InterviewStatus.CANCELLED);
        assertThat(interviewerResponse.cancelled()).isTrue();
        assertThat(interviewerResponse.guideMessage()).isNotBlank();
        assertThat(interviewerResponse.candidates())
                .extracting(candidate -> candidate.jobApplicationId())
                .containsExactly(application.getId());
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
                "memo"
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
