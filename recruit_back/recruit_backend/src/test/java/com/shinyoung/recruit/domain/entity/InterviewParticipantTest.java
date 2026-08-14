package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewParticipantTest {

    @Test
    void candidate_factory는_candidate_row를_생성한다() {
        Interview interview = interview();
        JobApplication application = jobApplication();

        InterviewParticipant participant = InterviewParticipant.candidate(interview, application, 1);

        assertThat(participant.getRole()).isEqualTo(InterviewParticipantRole.CANDIDATE);
        assertThat(participant.getJobApplication()).isEqualTo(application);
        assertThat(participant.getEmployee()).isNull();
        assertThat(participant.getParticipantStatus()).isEqualTo(InterviewParticipantStatus.ASSIGNED);
        assertThat(participant.getSortOrder()).isEqualTo(1);
    }

    @Test
    void interviewer_factory는_interviewer_row를_생성한다() {
        Interview interview = interview();
        Employee employee = employee();

        InterviewParticipant participant = InterviewParticipant.interviewer(interview, employee, 2);

        assertThat(participant.getRole()).isEqualTo(InterviewParticipantRole.INTERVIEWER);
        assertThat(participant.getEmployee()).isEqualTo(employee);
        assertThat(participant.getJobApplication()).isNull();
        assertThat(participant.getParticipantStatus()).isEqualTo(InterviewParticipantStatus.ASSIGNED);
        assertThat(participant.getSortOrder()).isEqualTo(2);
    }

    @Test
    void candidate_생성시_jobApplication이_null이면_실패한다() {
        assertThatThrownBy(() -> InterviewParticipant.candidate(interview(), null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interviewer_생성시_employee가_null이면_실패한다() {
        assertThatThrownBy(() -> InterviewParticipant.interviewer(interview(), null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancel_호출시_participantStatus가_CANCELLED가_된다() {
        InterviewParticipant participant = InterviewParticipant.candidate(interview(), jobApplication(), 1);

        participant.cancel();

        assertThat(participant.getParticipantStatus()).isEqualTo(InterviewParticipantStatus.CANCELLED);
    }

    @Test
    void 상태_확인_메서드가_의도대로_동작한다() {
        InterviewParticipant candidate = InterviewParticipant.candidate(interview(), jobApplication(), 1);
        InterviewParticipant interviewer = InterviewParticipant.interviewer(interview(), employee(), 1);

        assertThat(candidate.isCandidate()).isTrue();
        assertThat(candidate.isInterviewer()).isFalse();
        assertThat(candidate.isAssigned()).isTrue();
        assertThat(interviewer.isInterviewer()).isTrue();
        assertThat(interviewer.isCandidate()).isFalse();
        assertThat(interviewer.isAssigned()).isTrue();
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

    private JobApplication jobApplication() {
        JobPosting jobPosting = jobPosting();
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
