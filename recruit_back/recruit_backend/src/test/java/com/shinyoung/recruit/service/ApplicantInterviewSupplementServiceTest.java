package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementAnswerRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.InterviewSupplementAnswerItemRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementAnswerSaveRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionSaveRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowSaveRequest;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementFormResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSaveResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSupplementSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewSupplementNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewSupplementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 입력 가능 시간은 서버 Clock(시스템 시계)으로만 판정한다. 테스트는 같은 Clock 기준 상대 시각으로 창을 만든다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantInterviewSupplementServiceTest {

    @Autowired private ApplicantInterviewSupplementService service;
    @Autowired private InterviewSupplementAdminService adminService;
    @Autowired private Clock clock;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewParticipantRepository participantRepository;
    @Autowired private InterviewSupplementAnswerRepository answerRepository;

    @Test
    void 입력_시간_안이면_질문을_보고_답을_저장하며_빈_답은_지운다() {
        Fixture f = fixture(now().minusMinutes(30), 2);

        ApplicantInterviewSupplementFormResponse form =
                service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId());
        assertThat(form.questions()).hasSize(2);
        assertThat(form.endDateTime()).isEqualTo(f.arrival().plusHours(2));
        assertThat(form.remainingSeconds()).isBetween(80 * 60L, 90 * 60L);

        Long q1 = form.questions().get(0).questionId();
        Long q2 = form.questions().get(1).questionId();
        ApplicantInterviewSupplementSaveResponse saved = service.saveAnswers(
                f.applicant().getId(), f.application().getId(), f.stage().getId(),
                answers(item(q1, "첫 답"), item(q2, "둘째 답")));
        assertThat(saved.answeredCount()).isEqualTo(2);

        ApplicantInterviewSupplementSaveResponse cleared = service.saveAnswers(
                f.applicant().getId(), f.application().getId(), f.stage().getId(),
                answers(item(q2, "   ")));
        assertThat(cleared.answeredCount()).isEqualTo(1);
        assertThat(answerRepository.findAll()).hasSize(1);
        assertThat(service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId())
                .questions()).extracting(q -> q.answerText()).containsExactly("첫 답", null);
    }

    @Test
    void 종료_시각이_지나면_유예_없이_조회와_저장을_거부한다() {
        // 기본 창 = 도착 ~ +2시간. 도착을 2시간 1초 전으로 두면 1초 전에 끝난 창이 된다.
        Fixture f = fixture(now().minusHours(2).minusSeconds(1), 1);

        assertThatThrownBy(() -> service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId()))
                .isInstanceOf(InvalidInterviewSupplementException.class)
                .hasMessage("추가사항 입력 시간이 아닙니다.");
        assertThatThrownBy(() -> service.saveAnswers(f.applicant().getId(), f.application().getId(), f.stage().getId(),
                answers(item(f.questionIds().get(0), "늦은 답"))))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        assertThat(answerRepository.findAll()).isEmpty();
    }

    @Test
    void 시작_전이면_거부하고_관리자가_바꾼_지원자별_시간을_따른다() {
        Fixture f = fixture(now().plusHours(1), 1);

        assertThatThrownBy(() -> service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId()))
                .isInstanceOf(InvalidInterviewSupplementException.class);

        adminService.saveWindows(f.stage().getId(), new InterviewSupplementWindowSaveRequest(
                List.of(f.application().getId()), now().minusMinutes(5), now().plusMinutes(10)));

        assertThat(service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId())
                .remainingSeconds()).isBetween(9 * 60L, 10 * 60L);
    }

    @Test
    void 남의_지원서와_세트에_없는_질문은_거부한다() {
        Fixture f = fixture(now().minusMinutes(10), 1);
        Applicant other = saveApplicant();

        assertThatThrownBy(() -> service.getForm(other.getId(), f.application().getId(), f.stage().getId()))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
        assertThatThrownBy(() -> service.saveAnswers(f.applicant().getId(), f.application().getId(), f.stage().getId(),
                answers(item(Long.MAX_VALUE, "답"))))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        Long q = f.questionIds().get(0);
        assertThatThrownBy(() -> service.saveAnswers(f.applicant().getId(), f.application().getId(), f.stage().getId(),
                answers(item(q, "a"), item(q, "b"))))
                .isInstanceOf(InvalidInterviewSupplementException.class);
    }

    @Test
    void 질문이_없거나_꺼진_면접은_목록에_나오지_않고_404다() {
        Fixture f = fixture(now().minusMinutes(10), 0);

        assertThat(service.getMySupplements(f.applicant().getId())).isEmpty();
        assertThatThrownBy(() -> service.getForm(f.applicant().getId(), f.application().getId(), f.stage().getId()))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
    }

    @Test
    void 지원서마다_입력중인_추가사항을_먼저_보여준다() {
        Fixture f = fixture(now().minusMinutes(10), 1);
        // 같은 공고의 다음 면접 단계(예정)에도 추가사항이 있다.
        Stage next = stageRepository.saveAndFlush(
                Stage.create(f.posting(), "최종 면접", StageType.FINAL_INTERVIEW, 2, null, true));
        adminService.enable(next.getId());
        adminService.addQuestion(next.getId(), new InterviewSupplementQuestionSaveRequest("최종 질문"));
        Interview later = saveConfirmedInterview(f.posting(), next, "1", now().plusDays(3));
        participantRepository.saveAndFlush(InterviewParticipant.candidate(later, f.application(), 1));

        List<ApplicantInterviewSupplementSummaryResponse> summaries = service.getMySupplements(f.applicant().getId());

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).stageId()).isEqualTo(f.stage().getId());
        assertThat(summaries.get(0).open()).isTrue();
        assertThat(summaries.get(0).questionCount()).isEqualTo(1);
        assertThat(summaries.get(0).answeredCount()).isZero();
    }

    private record Fixture(
            JobPosting posting,
            Stage stage,
            Applicant applicant,
            JobApplication application,
            LocalDateTime arrival,
            List<Long> questionIds
    ) {
    }

    private Fixture fixture(LocalDateTime arrival, int questionCount) {
        JobPosting posting = JobPosting.create("Posting", "Content", arrival.minusDays(30), arrival.minusDays(10));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        Stage stage = stageRepository.saveAndFlush(
                Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false));
        Applicant applicant = saveApplicant();
        JobPosition position = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, position, "지원자", posting.getTitle(), position.getPositionName());
        application.submit(arrival.minusDays(20));
        application = jobApplicationRepository.saveAndFlush(application);
        Interview interview = saveConfirmedInterview(posting, stage, "1", arrival);
        participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));

        adminService.enable(stage.getId());
        for (int i = 1; i <= questionCount; i++) {
            adminService.addQuestion(stage.getId(), new InterviewSupplementQuestionSaveRequest("질문 " + i));
        }
        List<Long> questionIds = adminService.getSupplement(stage.getId()).questions().stream()
                .map(q -> q.questionId())
                .toList();
        return new Fixture(posting, stage, applicant, application, arrival, questionIds);
    }

    private Interview saveConfirmedInterview(JobPosting posting, Stage stage, String group, LocalDateTime arrival) {
        Interview interview = Interview.createDraft(posting, stage, group, arrival.plusMinutes(30), arrival,
                InterviewMethod.IN_PERSON, "본사", null, null, null);
        interview.confirm();
        return interviewRepository.saveAndFlush(interview);
    }

    private Applicant saveApplicant() {
        Applicant applicant = new Applicant(HashUtil.sha256("test-ci-" + UUID.randomUUID()));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private InterviewSupplementAnswerSaveRequest answers(InterviewSupplementAnswerItemRequest... items) {
        return new InterviewSupplementAnswerSaveRequest(List.of(items));
    }

    private InterviewSupplementAnswerItemRequest item(Long questionId, String text) {
        return new InterviewSupplementAnswerItemRequest(questionId, text);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
