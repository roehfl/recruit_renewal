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
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.dto.response.AdminApplicationEvaluationResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewEvaluationResponse;
import com.shinyoung.recruit.dto.response.InterviewEvaluationInitializeResponse;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewEvaluationNotFoundException;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewEvaluationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
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
class InterviewEvaluationAdminServiceTest {

    @Autowired
    private InterviewEvaluationAdminService interviewEvaluationAdminService;

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
    void initialize_createsAllAssignedCandidateInterviewerCombinations() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 2);
        saveInterviewer(interview, saveEmployee(), 1);
        saveInterviewer(interview, saveEmployee(), 2);
        saveInterviewer(interview, saveEmployee(), 3);

        InterviewEvaluationInitializeResponse response = interviewEvaluationAdminService.initialize(interview.getId());

        assertThat(response.interviewId()).isEqualTo(interview.getId());
        assertThat(response.createdCount()).isEqualTo(6);
        assertThat(response.alreadyExistedCount()).isZero();
        assertThat(response.totalCount()).isEqualTo(6);
        assertThat(evaluationRepository.findByInterviewId(interview.getId())).hasSize(6);
    }

    @Test
    void initialize_isIdempotentAndSkipsCancelledParticipants() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        InterviewParticipant cancelledInterviewer = InterviewParticipant.interviewer(interview, saveEmployee(), 2);
        cancelledInterviewer.cancel();
        participantRepository.saveAndFlush(cancelledInterviewer);

        InterviewEvaluationInitializeResponse first = interviewEvaluationAdminService.initialize(interview.getId());
        assertThat(first.createdCount()).isEqualTo(1);
        assertThat(first.alreadyExistedCount()).isZero();

        InterviewEvaluationInitializeResponse second = interviewEvaluationAdminService.initialize(interview.getId());
        assertThat(second.createdCount()).isZero();
        assertThat(second.alreadyExistedCount()).isEqualTo(1);
        assertThat(second.totalCount()).isEqualTo(1);
        assertThat(evaluationRepository.findByInterviewId(interview.getId())).hasSize(1);
    }

    @Test
    void initialize_rejectsNonConfirmedInterviewAndMissingInterview() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview draft = saveInterview(jobPosting, stage, InterviewStatus.DRAFT);
        Interview cancelled = saveInterview(jobPosting, stage, InterviewStatus.CANCELLED);

        assertThatThrownBy(() -> interviewEvaluationAdminService.initialize(draft.getId()))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
        assertThatThrownBy(() -> interviewEvaluationAdminService.initialize(cancelled.getId()))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
        assertThatThrownBy(() -> interviewEvaluationAdminService.initialize(999999L))
                .isInstanceOf(InterviewNotFoundException.class);
    }

    @Test
    void getInterviewEvaluations_groupsByCandidateWithSubmittedOnlySummary() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        saveInterviewer(interview, saveEmployee(), 2);
        saveInterviewer(interview, saveEmployee(), 3);
        interviewEvaluationAdminService.initialize(interview.getId());

        List<InterviewEvaluation> evaluations = evaluationRepository.findByInterviewId(interview.getId());
        submit(evaluations.get(0), EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);
        submit(evaluations.get(1), EvaluationGrade.G_PLUS, EvaluationRecommendation.YES);
        // evaluations.get(2) stays DRAFT

        AdminInterviewEvaluationResponse response = interviewEvaluationAdminService.getInterviewEvaluations(interview.getId());

        assertThat(response.interviewId()).isEqualTo(interview.getId());
        assertThat(response.candidates()).hasSize(1);
        var candidate = response.candidates().get(0);
        assertThat(candidate.evaluations()).hasSize(3);
        assertThat(candidate.summary().submittedCount()).isEqualTo(2);
        assertThat(candidate.summary().totalEvaluatorCount()).isEqualTo(3);
        assertThat(candidate.summary().gradeDistribution().vg()).isEqualTo(1);
        assertThat(candidate.summary().gradeDistribution().gPlus()).isEqualTo(1);
        assertThat(candidate.summary().gradeDistribution().g()).isZero();
        assertThat(candidate.summary().recommendationDistribution().strongYes()).isEqualTo(1);
        assertThat(candidate.summary().recommendationDistribution().yes()).isEqualTo(1);
        assertThat(candidate.summary().recommendationDistribution().neutral()).isZero();
        // admin view exposes interviewer identity
        assertThat(candidate.evaluations()).allSatisfy(item -> assertThat(item.interviewerName()).isNotBlank());
    }

    @Test
    void getStageEvaluations_returnsOnePerInterviewInStage() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview first = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        Interview second = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(first, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(first, saveEmployee(), 1);
        saveCandidate(second, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(second, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(first.getId());
        interviewEvaluationAdminService.initialize(second.getId());

        List<AdminInterviewEvaluationResponse> response = interviewEvaluationAdminService.getStageEvaluations(stage.getId());

        assertThat(response).extracting(AdminInterviewEvaluationResponse::interviewId)
                .containsExactly(first.getId(), second.getId());
        assertThat(response.get(0).candidates()).hasSize(1);
    }

    @Test
    void getApplicationEvaluations_returnsOnePerInterviewForFixedCandidate() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        JobApplication application = saveSubmittedApplication(jobPosting);
        Interview first = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        Interview second = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(first, application, 1);
        saveInterviewer(first, saveEmployee(), 1);
        saveCandidate(second, application, 1);
        saveInterviewer(second, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(first.getId());
        interviewEvaluationAdminService.initialize(second.getId());

        List<AdminApplicationEvaluationResponse> response =
                interviewEvaluationAdminService.getApplicationEvaluations(application.getId());

        assertThat(response).extracting(AdminApplicationEvaluationResponse::interviewId)
                .containsExactly(first.getId(), second.getId());
        assertThat(response.get(0).evaluations()).hasSize(1);
        assertThat(response.get(0).summary().totalEvaluatorCount()).isEqualTo(1);
    }

    @Test
    void readApis_rejectMissingInterviewStageApplication() {
        assertThatThrownBy(() -> interviewEvaluationAdminService.getInterviewEvaluations(999999L))
                .isInstanceOf(InterviewNotFoundException.class);
        assertThatThrownBy(() -> interviewEvaluationAdminService.getStageEvaluations(999999L))
                .isInstanceOf(StageNotFoundException.class);
        assertThatThrownBy(() -> interviewEvaluationAdminService.getApplicationEvaluations(999999L))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void reopen_transitionsSubmittedToDraftAndClearsSubmittedAt() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(interview.getId());
        InterviewEvaluation evaluation = evaluationRepository.findByInterviewId(interview.getId()).get(0);
        submit(evaluation, EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);

        var response = interviewEvaluationAdminService.reopen(interview.getId(), evaluation.getId(), "admin01");

        assertThat(response.status()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(response.submittedAt()).isNull();
        // grade/recommendation are preserved on reopen; only status and submittedAt change
        assertThat(response.grade()).isEqualTo(EvaluationGrade.VG);
        InterviewEvaluation reloaded = evaluationRepository.findById(evaluation.getId()).orElseThrow();
        assertThat(reloaded.isDraft()).isTrue();
        assertThat(reloaded.getSubmittedAt()).isNull();
    }

    @Test
    void reopen_rejectsDraftEvaluation() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(interview.getId());
        InterviewEvaluation draft = evaluationRepository.findByInterviewId(interview.getId()).get(0);

        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), draft.getId(), "admin01"))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void reopen_rejectsMissingEvaluationAndBlankActor() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);

        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), 999999L, "admin01"))
                .isInstanceOf(InterviewEvaluationNotFoundException.class);
        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), 999999L, "  "))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void reopen_rejectsCancelledInterview() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(interview.getId());
        InterviewEvaluation evaluation = evaluationRepository.findByInterviewId(interview.getId()).get(0);
        submit(evaluation, EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);
        interview.cancel();
        interviewRepository.saveAndFlush(interview);

        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), evaluation.getId(), "admin01"))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void reopen_rejectsCancelledCandidateParticipant() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        InterviewParticipant candidate = saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        saveInterviewer(interview, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(interview.getId());
        InterviewEvaluation evaluation = evaluationRepository.findByInterviewId(interview.getId()).get(0);
        submit(evaluation, EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);
        candidate.cancel();
        participantRepository.saveAndFlush(candidate);

        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), evaluation.getId(), "admin01"))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
    }

    @Test
    void reopen_rejectsCancelledInterviewerParticipant() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        InterviewParticipant interviewer = saveInterviewer(interview, saveEmployee(), 1);
        interviewEvaluationAdminService.initialize(interview.getId());
        InterviewEvaluation evaluation = evaluationRepository.findByInterviewId(interview.getId()).get(0);
        submit(evaluation, EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);
        interviewer.cancel();
        participantRepository.saveAndFlush(interviewer);

        assertThatThrownBy(() -> interviewEvaluationAdminService.reopen(interview.getId(), evaluation.getId(), "admin01"))
                .isInstanceOf(InvalidInterviewEvaluationException.class);
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

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
