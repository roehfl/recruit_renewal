package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.condition.MessageTargetCondition;
import com.shinyoung.recruit.dto.response.MessageTargetRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageTargetResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageTargetServiceTest {

    @Autowired
    private MessageTargetService messageTargetService;
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
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private InterviewParticipantRepository interviewParticipantRepository;

    private JobPosting posting;
    private Stage documentStage;
    private Stage interviewStage;

    @BeforeEach
    void setUp() {
        posting = saveJobPosting(LocalDateTime.now().minusDays(1), LocalDate.now().plusDays(3).atTime(18, 0));
        documentStage = saveStage("서류전형", StageType.DOCUMENT, 0);
        interviewStage = saveStage("1차 면접", StageType.FIRST_INTERVIEW, 1);
    }

    @Test
    void 결과발표는_발표된_전형의_결과_조건에_맞는_지원자와_연락처_변수를_준다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication withInfo = submitted("김합격");
        basicInfo(withInfo, "김기본", "010-1234-5678", "kim@example.com");
        result(withInfo, StageResultStatus.PASSED);
        JobApplication withoutInfo = submitted("이합격");
        result(withoutInfo, StageResultStatus.PASSED);
        result(submitted("박불합"), StageResultStatus.FAILED);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), StageResultStatus.PASSED, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(withInfo.getId(), withoutInfo.getId());
        MessageTargetRecipientResponse first = response.recipients().get(0);
        assertThat(first.name()).isEqualTo("김기본");
        assertThat(first.email()).isEqualTo("kim@example.com");
        assertThat(first.phone()).isEqualTo("010-1234-5678");
        assertThat(first.mailAvailable()).isTrue();
        assertThat(first.smsAvailable()).isTrue();
        assertThat(first.resultStatus()).isEqualTo(StageResultStatus.PASSED);
        assertThat(first.variables())
                .containsEntry("이름", "김기본")
                .containsEntry("공고명", "메시지 공고")
                .containsEntry("전형명", "서류전형");
        assertThat(first.missingVariables()).isEmpty();
        MessageTargetRecipientResponse second = response.recipients().get(1);
        assertThat(second.name()).isEqualTo("이합격");
        assertThat(second.phone()).isEqualTo("01000000000");
        assertThat(response.interviewGroups()).isEmpty();
        assertThat(response.sender().name()).isEqualTo("신영증권 채용담당");
        assertThat(response.sender().smsCallbackNumber()).isEqualTo("02-0000-0000");
    }

    @Test
    void 결과_전체는_대기와_철회_결과를_뺀다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication passed = submitted("가");
        result(passed, StageResultStatus.PASSED);
        JobApplication hold = submitted("나");
        result(hold, StageResultStatus.HOLD);
        result(submitted("다"), StageResultStatus.PENDING);
        result(submitted("라"), StageResultStatus.WITHDRAWN);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(passed.getId(), hold.getId());
    }

    @Test
    void 발표_전_전형은_결과발표에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("결과가 발표된 전형만 선택할 수 있습니다.");
    }

    @Test
    void 결과발표에_전형이_없으면_거부한다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("전형을 선택해야 합니다.");
    }

    @Test
    void 철회와_파기_지원서는_제외한다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication normal = submitted("정상");
        result(normal, StageResultStatus.PASSED);
        JobApplication withdrawn = submitted("철회");
        result(withdrawn, StageResultStatus.PASSED);
        withdrawn.withdraw(LocalDateTime.of(2026, 6, 3, 9, 0));
        jobApplicationRepository.saveAndFlush(withdrawn);
        JobApplication purged = submitted("파기");
        result(purged, StageResultStatus.PASSED);
        ReflectionTestUtils.setField(purged, "purgeResult", PurgeResult.PURGED);
        jobApplicationRepository.saveAndFlush(purged);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, documentStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(normal.getId());
    }

    @Test
    void 마감임박은_접수중_공고의_작성중_지원서만_준다() {
        setPostingStatus(JobPostingStatus.PUBLISHED);
        JobApplication draft = draft("최작성");
        submitted("정제출");

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.DEADLINE_REMINDER, null, null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(draft.getId());
        MessageTargetRecipientResponse recipient = response.recipients().get(0);
        assertThat(recipient.variables())
                .containsEntry("남은기간", "D-3")
                .containsEntry("마감일시", posting.getReceptionEndDateTime()
                        .format(DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)));
        assertThat(recipient.draftStartedAt()).isNotNull();
    }

    @Test
    void 접수중이_아닌_공고는_마감임박에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.DEADLINE_REMINDER, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("접수 중인 공고만 선택할 수 있습니다.");
    }

    @Test
    void 게시중이어도_접수기간이_아니면_마감임박에_쓸_수_없다() {
        setPostingStatus(JobPostingStatus.PUBLISHED);
        ReflectionTestUtils.setField(posting, "receptionEndDateTime", LocalDateTime.now().minusMinutes(1));
        jobPostingRepository.saveAndFlush(posting);

        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.DEADLINE_REMINDER, null, null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("접수 중인 공고만 선택할 수 있습니다.");
    }

    @Test
    void 면접은_확정_면접_배정자만_가장_이른_면접_값으로_준다() {
        JobApplication early = submitted("김면접");
        JobApplication late = submitted("이면접");
        JobApplication unconfirmed = submitted("박미확정");
        candidate(interview("2", LocalDateTime.of(2026, 10, 14, 14, 0), null, InterviewStatus.CONFIRMED), late);
        candidate(interview("1", LocalDateTime.of(2026, 10, 14, 9, 30), LocalDateTime.of(2026, 10, 14, 9, 10),
                InterviewStatus.CONFIRMED), early);
        candidate(interview("3", LocalDateTime.of(2026, 10, 14, 16, 0), null, InterviewStatus.DRAFT), unconfirmed);
        candidate(interview("4", LocalDateTime.of(2026, 10, 15, 9, 0), null, InterviewStatus.CONFIRMED), early);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_SCHEDULE, interviewStage.getId(), null, null, null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(early.getId(), late.getId());
        MessageTargetRecipientResponse first = response.recipients().get(0);
        assertThat(first.interviewGroup()).isEqualTo("1");
        assertThat(first.interviewDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 30));
        assertThat(first.variables())
                .containsEntry("면접일시", "2026-10-14(수) 09:30")
                .containsEntry("도착시각", "09:10")
                .containsEntry("면접장소", "본사 12층 대회의실")
                .containsEntry("면접방식", "대면")
                .containsEntry("조", "1조");
        assertThat(response.recipients().get(1).missingVariables()).containsExactly("도착시각", "접속링크");
        assertThat(response.interviewGroups()).containsExactly("1", "2", "4");
    }

    @Test
    void 조를_고르면_그_조_배정자만_준다() {
        JobApplication first = submitted("김면접");
        JobApplication second = submitted("이면접");
        candidate(interview("1", LocalDateTime.of(2026, 10, 14, 9, 30), null, InterviewStatus.CONFIRMED), first);
        candidate(interview("2", LocalDateTime.of(2026, 10, 14, 14, 0), null, InterviewStatus.CONFIRMED), second);

        MessageTargetResponse response = messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_NOTICE, interviewStage.getId(), null, "2", null));

        assertThat(response.recipients()).extracting(MessageTargetRecipientResponse::applicationId)
                .containsExactly(second.getId());
        assertThat(response.interviewGroups()).containsExactly("1", "2");
    }

    @Test
    void 면접_유형이_아닌_전형은_면접_안내에_쓸_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.INTERVIEW_SCHEDULE, documentStage.getId(), null, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("면접 전형만 선택할 수 있습니다.");
    }

    @Test
    void 직접입력은_지원_상태와_전형_결과로_거른다() {
        setStatus(documentStage, StageStatus.RESULT_ANNOUNCED);
        JobApplication passed = submitted("가");
        result(passed, StageResultStatus.PASSED);
        JobApplication failed = submitted("나");
        result(failed, StageResultStatus.FAILED);
        JobApplication draft = draft("다");

        assertThat(ids(condition(MessageType.FREE, null, null, null, JobApplicationStatus.SUBMITTED)))
                .containsExactly(passed.getId(), failed.getId());
        assertThat(ids(condition(MessageType.FREE, null, null, null, null)))
                .containsExactly(passed.getId(), failed.getId(), draft.getId());
        assertThat(ids(condition(MessageType.FREE, documentStage.getId(), StageResultStatus.PASSED, null,
                JobApplicationStatus.SUBMITTED)))
                .containsExactly(passed.getId());
    }

    @Test
    void 직접입력은_발표_전_전형으로_거를_수_없다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.FREE, documentStage.getId(), StageResultStatus.PASSED, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("결과가 발표된 전형만 선택할 수 있습니다.");
    }

    @Test
    void 직접입력에서_전형_없이_결과_조건만_주면_거부한다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.FREE, null, StageResultStatus.PASSED, null, null)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("결과 조건은 전형을 선택해야 쓸 수 있습니다.");
    }

    @Test
    void 연락처_형식이_틀리면_그_채널을_쓸_수_없다() {
        JobApplication application = submitted("홍형식");
        basicInfo(application, "홍형식", "010-12", "not-an-email");

        MessageTargetRecipientResponse recipient = messageTargetService.getTargets(
                condition(MessageType.FREE, null, null, null, null)).recipients().get(0);

        assertThat(recipient.mailAvailable()).isFalse();
        assertThat(recipient.smsAvailable()).isFalse();
    }

    @Test
    void 없는_공고는_404_예외다() {
        assertThatThrownBy(() -> messageTargetService.getTargets(
                new MessageTargetCondition(MessageType.FREE, Long.MAX_VALUE, null, null, null, null)))
                .isInstanceOf(JobPostingNotFoundException.class)
                .hasMessage("공고를 찾을 수 없습니다.");
    }

    @Test
    void 다른_공고의_전형은_404_예외다() {
        JobPosting otherPosting = JobPosting.create("다른 공고", "Content",
                LocalDateTime.now().minusDays(1), LocalDate.now().plusDays(3).atTime(18, 0));
        otherPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        otherPosting = jobPostingRepository.saveAndFlush(otherPosting);
        Stage otherStage = stageRepository.saveAndFlush(
                Stage.create(otherPosting, "타공고 서류", StageType.DOCUMENT, 0, null, false));

        assertThatThrownBy(() -> messageTargetService.getTargets(
                condition(MessageType.RESULT_ANNOUNCEMENT, otherStage.getId(), null, null, null)))
                .isInstanceOf(StageNotFoundException.class)
                .hasMessage("전형을 찾을 수 없습니다.");
    }

    private List<Long> ids(MessageTargetCondition condition) {
        return messageTargetService.getTargets(condition).recipients().stream()
                .map(MessageTargetRecipientResponse::applicationId)
                .toList();
    }

    private MessageTargetCondition condition(MessageType type, Long stageId, StageResultStatus resultStatus,
                                             String interviewGroup, JobApplicationStatus applicationStatus) {
        return new MessageTargetCondition(type, posting.getId(), stageId, resultStatus, interviewGroup, applicationStatus);
    }

    private JobPosting saveJobPosting(LocalDateTime start, LocalDateTime end) {
        JobPosting jobPosting = JobPosting.create("메시지 공고", "Content", start, end);
        jobPosting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private void setPostingStatus(JobPostingStatus status) {
        ReflectionTestUtils.setField(posting, "status", status);
        jobPostingRepository.saveAndFlush(posting);
    }

    private Stage saveStage(String name, StageType stageType, int stageOrder) {
        return stageRepository.saveAndFlush(Stage.create(posting, name, stageType, stageOrder, null, false));
    }

    private void setStatus(Stage stage, StageStatus status) {
        ReflectionTestUtils.setField(stage, "status", status);
        stageRepository.saveAndFlush(stage);
    }

    private JobApplication draft(String name) {
        Applicant applicant = saveApplicant(name);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        return jobApplicationRepository.saveAndFlush(JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName()));
    }

    private JobApplication submitted(String name) {
        JobApplication application = draft(name);
        application.submit(LocalDateTime.of(2026, 6, 1, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private void result(JobApplication application, StageResultStatus status) {
        StageResult result = StageResult.initialize(documentStage, application);
        if (status != StageResultStatus.PENDING) {
            result.updateResult(status, BigDecimal.valueOf(90), null, LocalDateTime.of(2026, 6, 2, 9, 0), "admin");
        }
        stageResultRepository.saveAndFlush(result);
    }

    private void basicInfo(JobApplication application, String name, String phone, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), phone, null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }

    private Applicant saveApplicant(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Interview interview(String groupName, LocalDateTime start, LocalDateTime arrival, InterviewStatus status) {
        Interview interview = Interview.createDraft(posting, interviewStage, groupName, start, arrival,
                InterviewMethod.IN_PERSON, "본사", "12층 대회의실", null, null);
        if (status == InterviewStatus.CONFIRMED) {
            interview.confirm();
        }
        return interviewRepository.saveAndFlush(interview);
    }

    private void candidate(Interview interview, JobApplication application) {
        interviewParticipantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));
    }
}
