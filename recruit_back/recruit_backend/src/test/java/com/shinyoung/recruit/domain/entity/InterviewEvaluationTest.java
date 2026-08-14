package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewEvaluationTest {

    @Test
    void initialize는_DRAFT_평가를_생성하고_비정규화_필드를_채운다() {
        Interview interview = interview();
        InterviewParticipant candidate = candidate(interview);
        InterviewParticipant interviewer = interviewer(interview);

        InterviewEvaluation evaluation = InterviewEvaluation.initialize(interview, candidate, interviewer);

        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(evaluation.getInterview()).isEqualTo(interview);
        assertThat(evaluation.getCandidateParticipant()).isEqualTo(candidate);
        assertThat(evaluation.getInterviewerParticipant()).isEqualTo(interviewer);
        assertThat(evaluation.getJobApplication()).isEqualTo(candidate.getJobApplication());
        assertThat(evaluation.getStage()).isEqualTo(interview.getStage());
        assertThat(evaluation.getGrade()).isNull();
        assertThat(evaluation.getRecommendation()).isNull();
        assertThat(evaluation.getComment()).isNull();
        assertThat(evaluation.getSubmittedAt()).isNull();
    }

    @Test
    void initialize시_candidateParticipant가_interviewer면_실패한다() {
        Interview interview = interview();
        InterviewParticipant interviewer = interviewer(interview);
        InterviewParticipant anotherInterviewer = InterviewParticipant.interviewer(interview, employee(), 2);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, anotherInterviewer, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_interviewerParticipant가_candidate면_실패한다() {
        Interview interview = interview();
        InterviewParticipant candidate = candidate(interview);
        InterviewParticipant anotherCandidate = InterviewParticipant.candidate(interview, jobApplication(interview.getJobPosting()), 2);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, candidate, anotherCandidate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_interview가_null이면_실패한다() {
        Interview interview = interview();
        InterviewParticipant candidate = candidate(interview);
        InterviewParticipant interviewer = interviewer(interview);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(null, candidate, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_candidateParticipant가_null이면_실패한다() {
        Interview interview = interview();
        InterviewParticipant interviewer = interviewer(interview);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, null, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_interviewerParticipant가_null이면_실패한다() {
        Interview interview = interview();
        InterviewParticipant candidate = candidate(interview);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, candidate, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_interview_stage가_null이면_실패한다() {
        Interview realInterview = interview();
        InterviewParticipant candidate = candidate(realInterview);
        InterviewParticipant interviewer = interviewer(realInterview);
        Interview stagelessInterview = mock(Interview.class);
        when(stagelessInterview.getStage()).thenReturn(null);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(stagelessInterview, candidate, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_candidateParticipant의_jobApplication이_null이면_실패한다() {
        Interview interview = interview();
        InterviewParticipant interviewer = interviewer(interview);
        InterviewParticipant candidateWithoutApplication = mock(InterviewParticipant.class);
        when(candidateWithoutApplication.isCandidate()).thenReturn(true);
        when(candidateWithoutApplication.getJobApplication()).thenReturn(null);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, candidateWithoutApplication, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_candidateParticipant가_다른_interview에_속하면_실패한다() {
        Interview interview = interview();
        Interview otherInterview = interview();
        InterviewParticipant otherCandidate = candidate(otherInterview);
        InterviewParticipant interviewer = interviewer(interview);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, otherCandidate, interviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialize시_interviewerParticipant가_다른_interview에_속하면_실패한다() {
        Interview interview = interview();
        Interview otherInterview = interview();
        InterviewParticipant candidate = candidate(interview);
        InterviewParticipant otherInterviewer = interviewer(otherInterview);

        assertThatThrownBy(() -> InterviewEvaluation.initialize(interview, candidate, otherInterviewer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateContent는_DRAFT_상태에서_내용을_갱신한다() {
        InterviewEvaluation evaluation = draftEvaluation();

        evaluation.updateContent(EvaluationGrade.G_PLUS, EvaluationRecommendation.YES, "  좋은 후보  ");

        assertThat(evaluation.getGrade()).isEqualTo(EvaluationGrade.G_PLUS);
        assertThat(evaluation.getRecommendation()).isEqualTo(EvaluationRecommendation.YES);
        assertThat(evaluation.getComment()).isEqualTo("좋은 후보");
    }

    @Test
    void updateContent는_grade와_recommendation_null을_허용한다() {
        InterviewEvaluation evaluation = draftEvaluation();

        evaluation.updateContent(null, null, null);

        assertThat(evaluation.getGrade()).isNull();
        assertThat(evaluation.getRecommendation()).isNull();
        assertThat(evaluation.getComment()).isNull();
    }

    @Test
    void updateContent시_comment가_2000자를_초과하면_실패한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        String tooLong = "a".repeat(InterviewEvaluation.COMMENT_MAX_LENGTH + 1);

        assertThatThrownBy(() -> evaluation.updateContent(EvaluationGrade.G, EvaluationRecommendation.NEUTRAL, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comment_2000자는_허용한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        String maxComment = "a".repeat(InterviewEvaluation.COMMENT_MAX_LENGTH);

        evaluation.updateContent(EvaluationGrade.G, EvaluationRecommendation.NEUTRAL, maxComment);

        assertThat(evaluation.getComment()).hasSize(InterviewEvaluation.COMMENT_MAX_LENGTH);
    }

    @Test
    void blank_comment는_null로_정규화된다() {
        InterviewEvaluation evaluation = draftEvaluation();

        evaluation.updateContent(EvaluationGrade.G, EvaluationRecommendation.NEUTRAL, "   ");

        assertThat(evaluation.getComment()).isNull();
    }

    @Test
    void submit는_grade와_recommendation이_있으면_SUBMITTED로_전이한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        evaluation.updateContent(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "추천");
        LocalDateTime submittedAt = LocalDateTime.of(2026, 6, 1, 12, 0);

        evaluation.submit(submittedAt);

        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(evaluation.getSubmittedAt()).isEqualTo(submittedAt);
    }

    @Test
    void submit시_grade가_없으면_실패한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        evaluation.updateContent(null, EvaluationRecommendation.YES, null);

        assertThatThrownBy(() -> evaluation.submit(LocalDateTime.of(2026, 6, 1, 12, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit시_recommendation이_없으면_실패한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        evaluation.updateContent(EvaluationGrade.G, null, null);

        assertThatThrownBy(() -> evaluation.submit(LocalDateTime.of(2026, 6, 1, 12, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit시_submittedAt이_null이면_실패한다() {
        InterviewEvaluation evaluation = draftEvaluation();
        evaluation.updateContent(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, null);

        assertThatThrownBy(() -> evaluation.submit(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void SUBMITTED_평가는_updateContent할_수_없다() {
        InterviewEvaluation evaluation = submittedEvaluation();

        assertThatThrownBy(() -> evaluation.updateContent(EvaluationGrade.F, EvaluationRecommendation.NO, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void SUBMITTED_평가는_다시_submit할_수_없다() {
        InterviewEvaluation evaluation = submittedEvaluation();

        assertThatThrownBy(() -> evaluation.submit(LocalDateTime.of(2026, 6, 2, 12, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reopen은_SUBMITTED를_DRAFT로_되돌리고_submittedAt을_초기화한다() {
        InterviewEvaluation evaluation = submittedEvaluation();

        evaluation.reopen();

        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(evaluation.getSubmittedAt()).isNull();
        assertThat(evaluation.getGrade()).isEqualTo(EvaluationGrade.VG);
        assertThat(evaluation.getRecommendation()).isEqualTo(EvaluationRecommendation.STRONG_YES);
    }

    @Test
    void DRAFT_평가는_reopen할_수_없다() {
        InterviewEvaluation evaluation = draftEvaluation();

        assertThatThrownBy(evaluation::reopen)
                .isInstanceOf(IllegalStateException.class);
    }

    private InterviewEvaluation draftEvaluation() {
        Interview interview = interview();
        return InterviewEvaluation.initialize(interview, candidate(interview), interviewer(interview));
    }

    private InterviewEvaluation submittedEvaluation() {
        InterviewEvaluation evaluation = draftEvaluation();
        evaluation.updateContent(EvaluationGrade.VG, EvaluationRecommendation.STRONG_YES, "추천");
        evaluation.submit(LocalDateTime.of(2026, 6, 1, 12, 0));
        return evaluation;
    }

    private InterviewParticipant candidate(Interview interview) {
        return InterviewParticipant.candidate(interview, jobApplication(interview.getJobPosting()), 1);
    }

    private InterviewParticipant interviewer(Interview interview) {
        return InterviewParticipant.interviewer(interview, employee(), 1);
    }

    private Interview interview() {
        JobPosting jobPosting = jobPosting();
        return Interview.createDraft(
                jobPosting,
                stage(jobPosting),
                "1조",
                start(),
                end(),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        );
    }

    private JobApplication jobApplication(JobPosting jobPosting) {
        JobPosition jobPosition = JobPosition.create("본사영업", 1);
        return JobApplication.create(
                new Applicant("test-ci", "test-ci-hash"),
                jobPosting,
                jobPosition,
                "지원자",
                "공고",
                "본사영업"
        );
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setLoginId("interviewer1");
        employee.setName("면접관");
        employee.setDeptName("인사부");
        return employee;
    }

    private JobPosting jobPosting() {
        return JobPosting.create("공고", "내용", start().minusDays(1), end().plusDays(1));
    }

    private Stage stage(JobPosting jobPosting) {
        return Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }

    private LocalDateTime end() {
        return LocalDateTime.of(2026, 6, 1, 11, 0);
    }
}
