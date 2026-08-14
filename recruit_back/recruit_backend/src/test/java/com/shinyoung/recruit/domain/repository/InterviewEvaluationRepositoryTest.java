package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
class InterviewEvaluationRepositoryTest {

    @Autowired
    private InterviewEvaluationRepository evaluationRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository participantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void 평가를_저장하고_interview로_조회한다() {
        Interview interview = saveInterview();
        InterviewParticipant candidate = saveCandidate(interview);
        InterviewParticipant interviewer = saveInterviewer(interview);

        InterviewEvaluation saved = evaluationRepository.saveAndFlush(
                InterviewEvaluation.initialize(interview, candidate, interviewer)
        );

        List<InterviewEvaluation> found = evaluationRepository.findByInterviewId(interview.getId());

        assertThat(found).extracting(InterviewEvaluation::getId).containsExactly(saved.getId());
        assertThat(found.get(0).getJobApplication().getId()).isEqualTo(candidate.getJobApplication().getId());
        assertThat(found.get(0).getStage().getId()).isEqualTo(interview.getStage().getId());
    }

    @Test
    void 저장된_grade와_recommendation이_유지된다() {
        Interview interview = saveInterview();
        InterviewParticipant candidate = saveCandidate(interview);
        InterviewParticipant interviewer = saveInterviewer(interview);
        InterviewEvaluation evaluation = InterviewEvaluation.initialize(interview, candidate, interviewer);
        evaluation.updateContent(EvaluationGrade.G_PLUS, EvaluationRecommendation.YES, "코멘트");
        evaluation.submit(LocalDateTime.of(2026, 6, 1, 12, 0));

        Long id = evaluationRepository.saveAndFlush(evaluation).getId();
        InterviewEvaluation found = evaluationRepository.findById(id).orElseThrow();

        assertThat(found.getGrade()).isEqualTo(EvaluationGrade.G_PLUS);
        assertThat(found.getRecommendation()).isEqualTo(EvaluationRecommendation.YES);
        assertThat(found.getComment()).isEqualTo("코멘트");
        assertThat(found.getSubmittedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 12, 0));
    }

    @Test
    void existsBy_유니크키_조회로_중복_초기화를_방지할_수_있다() {
        Interview interview = saveInterview();
        InterviewParticipant candidate = saveCandidate(interview);
        InterviewParticipant interviewer = saveInterviewer(interview);
        evaluationRepository.saveAndFlush(InterviewEvaluation.initialize(interview, candidate, interviewer));

        boolean exists = evaluationRepository.existsByInterviewIdAndCandidateParticipantIdAndInterviewerParticipantId(
                interview.getId(),
                candidate.getId(),
                interviewer.getId()
        );

        assertThat(exists).isTrue();
    }

    @Test
    void 동일_조합의_평가를_중복_저장하면_유니크_제약으로_실패한다() {
        Interview interview = saveInterview();
        InterviewParticipant candidate = saveCandidate(interview);
        InterviewParticipant interviewer = saveInterviewer(interview);
        evaluationRepository.saveAndFlush(InterviewEvaluation.initialize(interview, candidate, interviewer));

        assertThatThrownBy(() -> evaluationRepository.saveAndFlush(
                InterviewEvaluation.initialize(interview, candidate, interviewer)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private InterviewParticipant saveCandidate(Interview interview) {
        JobApplication application = saveApplication(interview.getJobPosting());
        return participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));
    }

    private InterviewParticipant saveInterviewer(Interview interview) {
        Employee employee = saveEmployee();
        return participantRepository.saveAndFlush(InterviewParticipant.interviewer(interview, employee, 1));
    }

    private Interview saveInterview() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = stageRepository.saveAndFlush(
                Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false)
        );
        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                "1조",
                start(),
                start().plusHours(1),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        );
        interview.confirm();
        return interviewRepository.saveAndFlush(interview);
    }

    private JobApplication saveApplication(JobPosting jobPosting) {
        Applicant applicant = saveApplicant();
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                "지원자",
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(start().minusDays(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private Applicant saveApplicant() {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Employee saveEmployee() {
        Employee employee = new Employee();
        employee.setLoginId("interviewer-" + UUID.randomUUID());
        employee.setName("면접관");
        employee.setDeptName("인사부-" + UUID.randomUUID());
        return employeeRepository.saveAndFlush(employee);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create("공고", "내용", start().minusDays(1), start().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("본사영업", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
