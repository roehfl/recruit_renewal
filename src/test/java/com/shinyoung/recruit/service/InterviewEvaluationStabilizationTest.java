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
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewEvaluationSaveRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewEvaluationResponse;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationDetailResponse;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 06e stabilization / regression coverage:
 * N×M initialize+read matrix, reopen→re-submit cycle, and StageResult non-mutation across the
 * interviewer submit, admin reopen, and admin read paths.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class InterviewEvaluationStabilizationTest {

    @Autowired
    private InterviewEvaluationAdminService adminService;

    @Autowired
    private InterviewerEvaluationService interviewerService;

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
    private StageResultRepository stageResultRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void nByMMatrix_initializesEveryCombinationAndAggregatesPerCandidate() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        for (int c = 1; c <= 3; c++) {
            saveCandidate(interview, saveSubmittedApplication(jobPosting), c);
        }
        for (int i = 1; i <= 4; i++) {
            saveInterviewer(interview, saveEmployee(), i);
        }

        var initializeResponse = adminService.initialize(interview.getId());
        assertThat(initializeResponse.createdCount()).isEqualTo(12);
        assertThat(initializeResponse.totalCount()).isEqualTo(12);

        // submit all 4 evaluations of the first candidate at entity level with mixed grades
        AdminInterviewEvaluationResponse beforeSubmit = adminService.getInterviewEvaluations(interview.getId());
        assertThat(beforeSubmit.candidates()).hasSize(3);
        assertThat(beforeSubmit.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.evaluations()).hasSize(4);
            assertThat(candidate.summary().totalEvaluatorCount()).isEqualTo(4);
            assertThat(candidate.summary().submittedCount()).isZero();
        });

        Long firstCandidateParticipantId = beforeSubmit.candidates().get(0).candidateParticipantId();
        List<InterviewEvaluation> firstCandidateEvaluations = evaluationRepository.findByInterviewId(interview.getId())
                .stream()
                .filter(evaluation -> evaluation.getCandidateParticipant().getId().equals(firstCandidateParticipantId))
                .toList();
        submitAtEntityLevel(firstCandidateEvaluations.get(0), EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES);
        submitAtEntityLevel(firstCandidateEvaluations.get(1), EvaluationGrade.VG, EvaluationRecommendation.YES);
        submitAtEntityLevel(firstCandidateEvaluations.get(2), EvaluationGrade.G_PLUS, EvaluationRecommendation.YES);
        submitAtEntityLevel(firstCandidateEvaluations.get(3), EvaluationGrade.G, EvaluationRecommendation.NEUTRAL);

        AdminInterviewEvaluationResponse afterSubmit = adminService.getInterviewEvaluations(interview.getId());
        var firstCandidate = afterSubmit.candidates().stream()
                .filter(candidate -> candidate.candidateParticipantId().equals(firstCandidateParticipantId))
                .findFirst()
                .orElseThrow();
        assertThat(firstCandidate.summary().submittedCount()).isEqualTo(4);
        assertThat(firstCandidate.summary().totalEvaluatorCount()).isEqualTo(4);
        var grade = firstCandidate.summary().gradeDistribution();
        assertThat(grade.vg() + grade.gPlus() + grade.g() + grade.gMinus() + grade.f()).isEqualTo(4);
        assertThat(grade.vg()).isEqualTo(2);
        assertThat(grade.gPlus()).isEqualTo(1);
        assertThat(grade.g()).isEqualTo(1);
        var recommendation = firstCandidate.summary().recommendationDistribution();
        assertThat(recommendation.strongYes()).isEqualTo(1);
        assertThat(recommendation.yes()).isEqualTo(2);
        assertThat(recommendation.neutral()).isEqualTo(1);
    }

    @Test
    void reopenReSubmitCycle_endToEnd() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, saveSubmittedApplication(jobPosting), 1);
        Employee interviewerEmployee = saveEmployee();
        saveInterviewer(interview, interviewerEmployee, 1);
        adminService.initialize(interview.getId());
        Long evaluationId = evaluationRepository.findByInterviewId(interview.getId()).get(0).getId();
        Long employeeId = interviewerEmployee.getId();

        // first submit
        InterviewerEvaluationDetailResponse firstSubmit = interviewerService.submit(
                employeeId,
                interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.G, EvaluationRecommendation.YES, "first")
        );
        assertThat(firstSubmit.status()).isEqualTo(EvaluationStatus.SUBMITTED);

        // admin reopen
        var reopened = adminService.reopen(interview.getId(), evaluationId, "admin01");
        assertThat(reopened.status()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(reopened.submittedAt()).isNull();

        // interviewer re-submits with new values
        InterviewerEvaluationDetailResponse reSubmit = interviewerService.submit(
                employeeId,
                interview.getId(),
                evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "second")
        );
        assertThat(reSubmit.status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(reSubmit.grade()).isEqualTo(EvaluationGrade.VG);
        assertThat(reSubmit.recommendation()).isEqualTo(EvaluationRecommendation.STRONG_YES);
        assertThat(reSubmit.comment()).isEqualTo("second");
        assertThat(reSubmit.submittedAt()).isNotNull();
    }

    @Test
    void evaluationSubmit_doesNotMutateStageResult() {
        Fixture fixture = confirmedInterviewWithStageResult();

        interviewerService.submit(
                fixture.interviewerEmployeeId,
                fixture.interviewId,
                fixture.evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "ok")
        );

        assertStageResultUntouched(fixture.stageResultId);
    }

    @Test
    void reopen_doesNotMutateStageResult() {
        Fixture fixture = confirmedInterviewWithStageResult();
        interviewerService.submit(
                fixture.interviewerEmployeeId,
                fixture.interviewId,
                fixture.evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "ok")
        );

        adminService.reopen(fixture.interviewId, fixture.evaluationId, "admin01");

        assertStageResultUntouched(fixture.stageResultId);
    }

    @Test
    void adminEvaluationRead_doesNotMutateStageResult() {
        Fixture fixture = confirmedInterviewWithStageResult();
        interviewerService.submit(
                fixture.interviewerEmployeeId,
                fixture.interviewId,
                fixture.evaluationId,
                new InterviewEvaluationSaveRequest(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "ok")
        );

        adminService.getInterviewEvaluations(fixture.interviewId);
        adminService.getStageEvaluations(fixture.stageId);
        adminService.getApplicationEvaluations(fixture.applicationId);

        assertStageResultUntouched(fixture.stageResultId);
    }

    private void assertStageResultUntouched(Long stageResultId) {
        StageResult stageResult = stageResultRepository.findById(stageResultId).orElseThrow();
        assertThat(stageResult.getResultStatus()).isEqualTo(StageResultStatus.PENDING);
        assertThat(stageResult.getScore()).isNull();
        assertThat(stageResult.getComment()).isNull();
        assertThat(stageResult.getDecidedAt()).isNull();
        assertThat(stageResult.getDecidedBy()).isNull();
        assertThat(stageResultRepository.count()).isEqualTo(1);
    }

    private Fixture confirmedInterviewWithStageResult() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        JobApplication application = saveSubmittedApplication(jobPosting);
        StageResult stageResult = stageResultRepository.saveAndFlush(StageResult.initialize(stage, application));
        Interview interview = saveInterview(jobPosting, stage, InterviewStatus.CONFIRMED);
        saveCandidate(interview, application, 1);
        Employee interviewerEmployee = saveEmployee();
        saveInterviewer(interview, interviewerEmployee, 1);
        adminService.initialize(interview.getId());
        Long evaluationId = evaluationRepository.findByInterviewId(interview.getId()).get(0).getId();
        return new Fixture(
                interview.getId(),
                stage.getId(),
                application.getId(),
                evaluationId,
                interviewerEmployee.getId(),
                stageResult.getId()
        );
    }

    private record Fixture(
            Long interviewId,
            Long stageId,
            Long applicationId,
            Long evaluationId,
            Long interviewerEmployeeId,
            Long stageResultId
    ) {
    }

    private void submitAtEntityLevel(InterviewEvaluation evaluation, EvaluationGrade grade, EvaluationRecommendation recommendation) {
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

    private java.time.LocalDateTime start() {
        return java.time.LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
