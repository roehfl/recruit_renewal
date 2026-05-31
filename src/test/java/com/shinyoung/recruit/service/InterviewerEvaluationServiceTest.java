package com.shinyoung.recruit.service;

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
import com.shinyoung.recruit.dto.request.InterviewEvaluationSaveRequest;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationListResponse;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewEvaluationNotFoundException;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewEvaluationException;
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
class InterviewerEvaluationServiceTest {

    @Autowired
    private InterviewerEvaluationService interviewerEvaluationService;

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

    @Test
    void getMyEvaluations_returnsOnlyOwnEvaluationsAndInterviewContext() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();

        InterviewerEvaluationListResponse response = interviewerEvaluationService.getMyEvaluations(
                employeeId(fixture.interviewerA),
                fixture.interview.getId()
        );

        assertThat(response.interviewId()).isEqualTo(fixture.interview.getId());
        assertThat(response.interviewStatus()).isEqualTo(InterviewStatus.CONFIRMED);
        assertThat(response.evaluations()).hasSize(1);
        assertThat(response.evaluations().get(0).candidateName()).isNotBlank();
        assertThat(response.evaluations().get(0).status()).isEqualTo(EvaluationStatus.DRAFT);
    }

    @Test
    void getMyEvaluations_rejectsNonAssignedInterviewer() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Employee stranger = saveEmployee();

        assertThatThrownBy(() -> interviewerEvaluationService.getMyEvaluations(stranger.getId(), fixture.interview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void getMyEvaluations_rejectsCancelledInterviewerAndDoesNotExposeTheirEvaluations() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long employeeId = employeeId(fixture.interviewerA);
        // sanity: evaluation rows exist for this interviewer before cancellation
        assertThat(evaluationRepository.findByInterviewIdAndInterviewerParticipantId(
                fixture.interview.getId(), fixture.interviewerA.getId())).hasSize(1);

        fixture.interviewerA.cancel();
        participantRepository.saveAndFlush(fixture.interviewerA);

        assertThatThrownBy(() -> interviewerEvaluationService.getMyEvaluations(employeeId, fixture.interview.getId()))
                .isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void getMyEvaluationDetail_hidesOtherInterviewerEvaluation() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationOfB = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerB);

        // interviewer A cannot read interviewer B's evaluation
        assertThatThrownBy(() -> interviewerEvaluationService.getMyEvaluationDetail(
                employeeId(fixture.interviewerA),
                fixture.interview.getId(),
                evaluationOfB
        )).isInstanceOf(InterviewEvaluationNotFoundException.class);
    }

    @Test
    void getMyEvaluationDetail_rejectsCancelledInterviewerParticipant() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long employeeId = employeeId(fixture.interviewerA);
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);

        fixture.interviewerA.cancel();
        participantRepository.saveAndFlush(fixture.interviewerA);

        // The cancelled interviewer knows the evaluation id, but detail read must be blocked.
        assertThatThrownBy(() -> interviewerEvaluationService.getMyEvaluationDetail(
                employeeId,
                fixture.interview.getId(),
                evaluationId
        )).isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void getMyEvaluationDetail_rejectsDraftInterview() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview draft = saveInterview(jobPosting, stage, InterviewStatus.DRAFT);
        Employee employee = saveEmployee();
        saveInterviewer(draft, employee, 1);

        // DRAFT interviews are not visible, so no evaluation detail is reachable.
        assertThatThrownBy(() -> interviewerEvaluationService.getMyEvaluationDetail(
                employee.getId(),
                draft.getId(),
                1L
        )).isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void save_updatesDraftContent() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);

        InterviewerEvaluationDetailResponse response = interviewerEvaluationService.save(
                employeeId(fixture.interviewerA),
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G_PLUS, EvaluationRecommendation.YES, "good")
        );

        assertThat(response.status()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(response.grade()).isEqualTo(EvaluationGrade.G_PLUS);
        assertThat(response.recommendation()).isEqualTo(EvaluationRecommendation.YES);
        assertThat(response.comment()).isEqualTo("good");
    }

    @Test
    void submit_setsSubmittedAndBlocksFurtherSave() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);

        InterviewerEvaluationDetailResponse submitted = interviewerEvaluationService.submit(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "excellent")
        );

        assertThat(submitted.status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();

        assertThatThrownBy(() -> interviewerEvaluationService.save(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.F, EvaluationRecommendation.NO, "changed")
        )).isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void submit_withoutBodyUsesPreviouslySavedValues() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);
        interviewerEvaluationService.save(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G, EvaluationRecommendation.NEUTRAL, "saved")
        );

        InterviewerEvaluationDetailResponse submitted = interviewerEvaluationService.submit(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                null
        );

        assertThat(submitted.status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(submitted.grade()).isEqualTo(EvaluationGrade.G);
        assertThat(submitted.recommendation()).isEqualTo(EvaluationRecommendation.NEUTRAL);
        assertThat(submitted.comment()).isEqualTo("saved");
    }

    @Test
    void submit_withoutBodyOnEmptyDraftRequiresGradeAndRecommendation() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);

        assertThatThrownBy(() -> interviewerEvaluationService.submit(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                null
        )).isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void submit_requiresGradeAndRecommendation() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);

        assertThatThrownBy(() -> interviewerEvaluationService.submit(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(null, EvaluationRecommendation.YES, "missing grade")
        )).isInstanceOf(InvalidInterviewEvaluationException.class);

        assertThatThrownBy(() -> interviewerEvaluationService.submit(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G, null, "missing recommendation")
        )).isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void save_blockedWhenInterviewCancelled() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);
        fixture.interview.cancel();
        interviewRepository.saveAndFlush(fixture.interview);

        assertThatThrownBy(() -> interviewerEvaluationService.save(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G, EvaluationRecommendation.YES, null)
        )).isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void save_blockedWhenCandidateParticipantCancelled() {
        Fixture fixture = confirmedInterviewWithTwoInterviewers();
        Long evaluationId = ownedEvaluationId(fixture.interview.getId(), fixture.interviewerA);
        Long employeeId = employeeId(fixture.interviewerA);
        fixture.candidate.cancel();
        participantRepository.saveAndFlush(fixture.candidate);

        assertThatThrownBy(() -> interviewerEvaluationService.save(
                employeeId,
                fixture.interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G, EvaluationRecommendation.YES, null)
        )).isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    private Long employeeId(InterviewParticipant interviewer) {
        return interviewer.getEmployee().getId();
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
        InterviewParticipant candidate = saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        InterviewParticipant interviewerA = saveInterviewer(interview, saveEmployee(), 1);
        InterviewParticipant interviewerB = saveInterviewer(interview, saveEmployee(), 2);
        adminService.initialize(interview.getId());
        return new Fixture(interview, candidate, interviewerA, interviewerB);
    }

    private record Fixture(
            Interview interview,
            InterviewParticipant candidate,
            InterviewParticipant interviewerA,
            InterviewParticipant interviewerB
    ) {
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
