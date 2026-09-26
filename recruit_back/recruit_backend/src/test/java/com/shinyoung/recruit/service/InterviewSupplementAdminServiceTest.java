package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.InterviewSupplementAnswer;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementAnswerRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementQuestionRepository;
import com.shinyoung.recruit.domain.repository.InterviewSupplementRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionReorderRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementQuestionSaveRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowResetRequest;
import com.shinyoung.recruit.dto.request.InterviewSupplementWindowSaveRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementAnswerDetailResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementCandidateResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementQuestionResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSupplementResponse;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewSupplementNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewSupplementException;
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
class InterviewSupplementAdminServiceTest {

    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 10, 6, 9, 30);

    @Autowired private InterviewSupplementAdminService service;
    @Autowired private StageService stageService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewParticipantRepository participantRepository;
    @Autowired private InterviewSupplementRepository supplementRepository;
    @Autowired private InterviewSupplementQuestionRepository questionRepository;
    @Autowired private InterviewSupplementAnswerRepository answerRepository;

    @Test
    void 면접_단계에서만_켤_수_있고_켜기는_멱등이다() {
        JobPosting posting = savePosting();
        Stage document = stageRepository.saveAndFlush(
                Stage.create(posting, "서류", StageType.DOCUMENT, 0, null, false));
        Stage interview = saveInterviewStage(posting, 1);

        assertThatThrownBy(() -> service.enable(document.getId()))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        assertThat(service.getSupplement(interview.getId()).enabled()).isFalse();

        service.enable(interview.getId());
        AdminInterviewSupplementResponse again = service.enable(interview.getId());

        assertThat(again.enabled()).isTrue();
        assertThat(supplementRepository.findByStageId(interview.getId())).isPresent();
    }

    @Test
    void 질문을_추가_수정_순서변경_삭제하면_번호가_이어진다() {
        Stage stage = saveInterviewStage(savePosting(), 1);
        service.enable(stage.getId());
        service.addQuestion(stage.getId(), question("  첫 질문  "));
        service.addQuestion(stage.getId(), question("둘째 질문"));
        AdminInterviewSupplementResponse added = service.addQuestion(stage.getId(), question("셋째 질문"));
        List<Long> ids = added.questions().stream().map(AdminInterviewSupplementQuestionResponse::questionId).toList();

        assertThat(added.questions()).extracting(AdminInterviewSupplementQuestionResponse::content)
                .containsExactly("첫 질문", "둘째 질문", "셋째 질문");

        service.updateQuestion(stage.getId(), ids.get(1), question("고친 둘째"));
        AdminInterviewSupplementResponse reordered = service.reorderQuestions(stage.getId(),
                new InterviewSupplementQuestionReorderRequest(List.of(ids.get(2), ids.get(0), ids.get(1))));
        assertThat(reordered.questions()).extracting(AdminInterviewSupplementQuestionResponse::content)
                .containsExactly("셋째 질문", "첫 질문", "고친 둘째");

        AdminInterviewSupplementResponse deleted = service.deleteQuestion(stage.getId(), ids.get(2));
        assertThat(deleted.questions()).extracting(AdminInterviewSupplementQuestionResponse::sortOrder)
                .containsExactly(1, 2);
        assertThat(deleted.questions()).extracting(AdminInterviewSupplementQuestionResponse::content)
                .containsExactly("첫 질문", "고친 둘째");
    }

    @Test
    void 순서변경_목록이_현재_질문과_다르면_거부한다() {
        Stage stage = saveInterviewStage(savePosting(), 1);
        service.enable(stage.getId());
        Long first = service.addQuestion(stage.getId(), question("A")).questions().get(0).questionId();
        service.addQuestion(stage.getId(), question("B"));

        assertThatThrownBy(() -> service.reorderQuestions(stage.getId(),
                new InterviewSupplementQuestionReorderRequest(List.of(first, first))))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        assertThatThrownBy(() -> service.reorderQuestions(stage.getId(),
                new InterviewSupplementQuestionReorderRequest(List.of(first))))
                .isInstanceOf(InvalidInterviewSupplementException.class);
    }

    @Test
    void 꺼진_단계의_질문_명령은_404다() {
        Stage stage = saveInterviewStage(savePosting(), 1);

        assertThatThrownBy(() -> service.addQuestion(stage.getId(), question("A")))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
        assertThatThrownBy(() -> service.getCandidates(stage.getId()))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
    }

    @Test
    void 대상_지원자의_기본_입력시간은_조_도착시간부터_2시간이다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        JobApplication first = saveApplication(posting);
        JobApplication second = saveApplication(posting);
        JobApplication noArrival = saveApplication(posting);
        JobApplication withdrawn = saveApplication(posting);
        withdrawn.withdraw(ARRIVAL.minusDays(1));
        Interview group1 = saveConfirmedInterview(posting, stage, "1", ARRIVAL);
        Interview group2 = saveConfirmedInterview(posting, stage, "2", null);
        saveCandidate(group1, first, 1);
        saveCandidate(group1, second, 2);
        saveCandidate(group1, withdrawn, 3);
        saveCandidate(group2, noArrival, 1);

        List<AdminInterviewSupplementCandidateResponse> rows = service.getCandidates(stage.getId());

        assertThat(rows).extracting(AdminInterviewSupplementCandidateResponse::jobApplicationId)
                .containsExactly(first.getId(), second.getId(), noArrival.getId());
        assertThat(rows.get(0).startDateTime()).isEqualTo(ARRIVAL);
        assertThat(rows.get(0).endDateTime()).isEqualTo(ARRIVAL.plusHours(2));
        assertThat(rows.get(0).customized()).isFalse();
        assertThat(rows.get(0).groupName()).isEqualTo("1");
        assertThat(rows.get(1).candidateOrder()).isEqualTo(2);
        assertThat(rows.get(2).startDateTime()).isNull();
        assertThat(rows.get(2).endDateTime()).isNull();
    }

    @Test
    void 지원자별로_시간을_바꾸고_기본값과_같으면_기본값으로_돌아간다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        JobApplication first = saveApplication(posting);
        JobApplication second = saveApplication(posting);
        Interview group = saveConfirmedInterview(posting, stage, "1", ARRIVAL);
        saveCandidate(group, first, 1);
        saveCandidate(group, second, 2);

        List<AdminInterviewSupplementCandidateResponse> changed = service.saveWindows(stage.getId(),
                new InterviewSupplementWindowSaveRequest(List.of(first.getId()), ARRIVAL, ARRIVAL.plusHours(3)));
        assertThat(changed.get(0).customized()).isTrue();
        assertThat(changed.get(0).endDateTime()).isEqualTo(ARRIVAL.plusHours(3));
        assertThat(changed.get(1).customized()).isFalse();

        List<AdminInterviewSupplementCandidateResponse> sameAsDefault = service.saveWindows(stage.getId(),
                new InterviewSupplementWindowSaveRequest(List.of(first.getId()), ARRIVAL, ARRIVAL.plusHours(2)));
        assertThat(sameAsDefault.get(0).customized()).isFalse();

        service.saveWindows(stage.getId(), new InterviewSupplementWindowSaveRequest(
                List.of(first.getId(), second.getId()), ARRIVAL.plusHours(1), ARRIVAL.plusHours(4)));
        List<AdminInterviewSupplementCandidateResponse> reset = service.resetWindows(stage.getId(),
                new InterviewSupplementWindowResetRequest(List.of(second.getId())));
        assertThat(reset.get(0).customized()).isTrue();
        assertThat(reset.get(1).customized()).isFalse();
        assertThat(reset.get(1).startDateTime()).isEqualTo(ARRIVAL);
    }

    @Test
    void 시간_순서가_틀리거나_대상이_아닌_지원자는_거부한다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        JobApplication candidate = saveApplication(posting);
        JobApplication outsider = saveApplication(posting);
        saveCandidate(saveConfirmedInterview(posting, stage, "1", ARRIVAL), candidate, 1);

        assertThatThrownBy(() -> service.saveWindows(stage.getId(), new InterviewSupplementWindowSaveRequest(
                List.of(candidate.getId()), ARRIVAL, ARRIVAL)))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        assertThatThrownBy(() -> service.saveWindows(stage.getId(), new InterviewSupplementWindowSaveRequest(
                List.of(outsider.getId()), ARRIVAL, ARRIVAL.plusHours(1))))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
        assertThatThrownBy(() -> service.getAnswers(stage.getId(), outsider.getId()))
                .isInstanceOf(InterviewSupplementNotFoundException.class);
    }

    @Test
    void 답변이_있으면_질문_삭제와_끄기를_막고_답변을_조회할_수_있다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        JobApplication application = saveApplication(posting);
        saveCandidate(saveConfirmedInterview(posting, stage, "1", ARRIVAL), application, 1);
        List<AdminInterviewSupplementQuestionResponse> questions =
                service.addQuestion(stage.getId(), question("질문 1")).questions();
        questions = service.addQuestion(stage.getId(), question("질문 2")).questions();
        answerRepository.saveAndFlush(InterviewSupplementAnswer.create(
                questionRepository.findById(questions.get(0).questionId()).orElseThrow(), application, "답변 1"));
        Long answeredQuestionId = questions.get(0).questionId();

        assertThatThrownBy(() -> service.deleteQuestion(stage.getId(), answeredQuestionId))
                .isInstanceOf(InvalidInterviewSupplementException.class);
        assertThatThrownBy(() -> service.disable(stage.getId()))
                .isInstanceOf(InvalidInterviewSupplementException.class);

        AdminInterviewSupplementAnswerDetailResponse detail = service.getAnswers(stage.getId(), application.getId());
        assertThat(detail.answeredCount()).isEqualTo(1);
        assertThat(detail.items()).extracting(item -> item.answerText()).containsExactly("답변 1", null);
        assertThat(service.getSupplement(stage.getId()).answeredApplicantCount()).isEqualTo(1);
        assertThat(service.getCandidates(stage.getId()).get(0).answeredCount()).isEqualTo(1);
    }

    @Test
    void 답변이_없으면_끄기로_질문과_지원자별_시간을_함께_지운다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        JobApplication application = saveApplication(posting);
        saveCandidate(saveConfirmedInterview(posting, stage, "1", ARRIVAL), application, 1);
        service.addQuestion(stage.getId(), question("질문"));
        service.saveWindows(stage.getId(), new InterviewSupplementWindowSaveRequest(
                List.of(application.getId()), ARRIVAL, ARRIVAL.plusHours(5)));

        AdminInterviewSupplementResponse disabled = service.disable(stage.getId());

        assertThat(disabled.enabled()).isFalse();
        assertThat(supplementRepository.findByStageId(stage.getId())).isEmpty();
        assertThat(questionRepository.findAll()).noneMatch(q -> q.getSupplement().getStage().getId().equals(stage.getId()));
    }

    @Test
    void 단계를_삭제하면_추가사항_질문_세트도_지운다() {
        JobPosting posting = savePosting();
        Stage stage = saveInterviewStage(posting, 1);
        service.enable(stage.getId());
        service.addQuestion(stage.getId(), question("질문"));

        stageService.delete(posting.getId(), stage.getId());

        assertThat(stageRepository.findById(stage.getId())).isEmpty();
        assertThat(supplementRepository.findByStageId(stage.getId())).isEmpty();
    }

    private InterviewSupplementQuestionSaveRequest question(String content) {
        return new InterviewSupplementQuestionSaveRequest(content);
    }

    private JobPosting savePosting() {
        JobPosting posting = JobPosting.create("Posting", "Content", ARRIVAL.minusDays(30), ARRIVAL.minusDays(10));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(posting);
    }

    private Stage saveInterviewStage(JobPosting posting, int order) {
        return stageRepository.saveAndFlush(
                Stage.create(posting, "1차 면접", StageType.FIRST_INTERVIEW, order, null, false));
    }

    private Interview saveConfirmedInterview(JobPosting posting, Stage stage, String group, LocalDateTime arrival) {
        Interview interview = Interview.createDraft(posting, stage, group, ARRIVAL.plusMinutes(30), arrival,
                InterviewMethod.IN_PERSON, "본사", null, null, null);
        interview.confirm();
        return interviewRepository.saveAndFlush(interview);
    }

    private void saveCandidate(Interview interview, JobApplication application, int order) {
        participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, order));
    }

    private JobApplication saveApplication(JobPosting posting) {
        Applicant applicant = new Applicant(HashUtil.sha256("test-ci-" + UUID.randomUUID()));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.saveAndFlush(applicant);
        JobPosition position = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, position, "지원자", posting.getTitle(), position.getPositionName());
        application.submit(ARRIVAL.minusDays(20));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
