package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.condition.MessageHistoryCondition;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.exception.MessageSendNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageHistoryServiceTest {

    @Autowired
    private MessageHistoryService messageHistoryService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private Clock clock;

    private JobPosting posting;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        posting = JobPosting.create("이력 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
    }

    @Test
    void 목록은_기간_종류_공고_구분으로_거르고_최신순으로_준다() {
        MessageSend dayAgo = saveSend(MessageType.FREE, false, true, true, now.minusDays(1));
        MessageSend announcement = saveSend(MessageType.RESULT_ANNOUNCEMENT, false, true, true, now.minusHours(2));
        MessageSend test = saveSend(MessageType.FREE, true, true, true, now.minusHours(1));
        MessageSend old = saveSend(MessageType.FREE, false, true, true, now.minusDays(40));

        assertThat(ids(search(null, null, null, null)))
                .containsExactly(test.getId(), announcement.getId(), dayAgo.getId());
        assertThat(ids(search(null, null, MessageType.FREE, null)))
                .containsExactly(test.getId(), dayAgo.getId());
        assertThat(ids(search(null, null, null, true))).containsExactly(test.getId());
        assertThat(ids(search(null, null, null, false))).containsExactly(announcement.getId(), dayAgo.getId());
        assertThat(ids(search(now.toLocalDate().minusDays(45), null, null, null)))
                .containsExactly(test.getId(), announcement.getId(), dayAgo.getId(), old.getId());
    }

    @Test
    void 발송_구분으로_거르고_공고_없는_시스템_발송은_공고_조건에서_빠진다() {
        MessageSend admin = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(2));
        MessageSend system = messageSendRepository.saveAndFlush(MessageSend.createSystem(
                MessageType.SIGNUP_VERIFICATION, null,
                MessageTemplate.create(MessageType.SIGNUP_VERIFICATION, "회원가입 인증 메일", true,
                        "[신영증권 채용] 회원가입 이메일 인증번호", "인증번호: #{인증번호}", null),
                now.minusMinutes(1)));

        List<Long> systemOnly = ids(messageHistoryService.search(
                new MessageHistoryCondition(null, null, null, null, null, MessageOrigin.SYSTEM), 0, 100));
        List<Long> adminOnly = ids(messageHistoryService.search(
                new MessageHistoryCondition(null, null, null, null, null, MessageOrigin.ADMIN), 0, 100));

        assertThat(systemOnly).contains(system.getId()).doesNotContain(admin.getId());
        assertThat(adminOnly).contains(admin.getId()).doesNotContain(system.getId());
        assertThat(ids(search(null, null, null, null))).containsExactly(admin.getId());
        MessageSendSummaryResponse summary = messageHistoryService.search(
                        new MessageHistoryCondition(null, null, MessageType.SIGNUP_VERIFICATION, null, null, MessageOrigin.SYSTEM), 0, 100)
                .content().stream()
                .filter(row -> row.id().equals(system.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(summary.origin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(summary.jobPostingTitle()).isNull();
        assertThat(messageHistoryService.detail(system.getId()).origin()).isEqualTo(MessageOrigin.SYSTEM);
    }

    @Test
    void 건수와_상태를_수신자_채널_상태로_계산한다() {
        MessageSend sending = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(5));
        saveRecipient(sending, null, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        saveRecipient(sending, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED);
        MessageSend resultPending = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(4));
        saveRecipient(resultPending, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SENT);
        saveRecipient(resultPending, null, MessageDeliveryStatus.FAILED, MessageDeliveryStatus.SKIPPED);
        MessageSend completed = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(3));
        saveRecipient(completed, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.FAILED);

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(sending.getId()).status()).isEqualTo(MessageSendStatus.SENDING);
        assertThat(byId.get(sending.getId()).mail()).isEqualTo(new MessageChannelCountResponse(1, 0, 1, 0, 0));
        assertThat(byId.get(sending.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 0, 0, 2));
        assertThat(byId.get(resultPending.getId()).status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(byId.get(resultPending.getId()).mail()).isEqualTo(new MessageChannelCountResponse(0, 1, 0, 1, 0));
        assertThat(byId.get(resultPending.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 1, 0, 1));
        assertThat(byId.get(completed.getId()).status()).isEqualTo(MessageSendStatus.COMPLETED);
        assertThat(byId.get(completed.getId()).sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 0, 1, 0));
    }

    @Test
    void 결과_대기_시간이_지나도_완료가_아니면_지연으로_표시한다() {
        MessageSend late = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(61));
        saveRecipient(late, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SKIPPED);
        MessageSend recent = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(10));
        saveRecipient(recent, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.SKIPPED);
        MessageSend lateCompleted = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(120));
        saveRecipient(lateCompleted, null, MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED);

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(late.getId()).delayed()).isTrue();
        assertThat(byId.get(late.getId()).status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(byId.get(recent.getId()).delayed()).isFalse();
        assertThat(byId.get(lateCompleted.getId()).delayed()).isFalse();
    }

    @Test
    void 발송_중_상태로_대기_시간이_지나면_SENDING이면서_지연으로_표시한다() {
        MessageSend stuck = saveSend(MessageType.FREE, false, true, false, now.minusMinutes(61));
        saveRecipient(stuck, null, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(stuck.getId()).status()).isEqualTo(MessageSendStatus.SENDING);
        assertThat(byId.get(stuck.getId()).delayed()).isTrue();
    }

    @Test
    void 제목은_메일_제목이고_메일을_끈_발송은_SMS_앞_40자다() {
        MessageSend mail = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(2));
        MessageSend smsOnly = messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                false, true, null, null, "가".repeat(50), "hr.kim", "김인사", 1, now.minusMinutes(1)));

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        assertThat(byId.get(mail.getId()).title()).isEqualTo("[신영증권] #{이름}님 안내");
        assertThat(byId.get(smsOnly.getId()).title()).isEqualTo("가".repeat(40));
        assertThat(byId.get(smsOnly.getId()).mailEnabled()).isFalse();
        assertThat(byId.get(mail.getId()).jobPostingTitle()).isEqualTo("이력 공고");
        assertThat(byId.get(mail.getId()).senderName()).isEqualTo("김인사");
    }

    @Test
    void SMS_제목은_이모지_서로게이트_쌍을_쪼개지_않는다() {
        String smsBody = "a".repeat(39) + "😀" + "그 다음 내용";
        MessageSend smsOnly = messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                false, true, null, null, smsBody, "hr.kim", "김인사", 1, now));

        Map<Long, MessageSendSummaryResponse> byId = summariesById();

        String title = byId.get(smsOnly.getId()).title();
        assertThat(title).endsWith("😀");
        assertThat(title.codePointCount(0, title.length())).isEqualTo(40);
    }

    @Test
    void 상세는_원문과_수신자별_결과와_연락처를_그대로_주고_파기된_수신자는_비어_있다() {
        JobApplication application = submitted("김지원");
        MessageSend send = saveSend(MessageType.FREE, false, true, true, now.minusMinutes(1));
        MessageRecipient applicant = saveRecipient(send, application, MessageDeliveryStatus.SENT, MessageDeliveryStatus.FAILED);
        MessageRecipient purged = messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, application, null, null, null,
                MessageDeliveryStatus.SENT, null, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT, null));
        MessageRecipient tester = saveRecipient(send, null, MessageDeliveryStatus.REQUESTED, MessageDeliveryStatus.REQUESTED);

        MessageSendDetailResponse detail = messageHistoryService.detail(send.getId());

        assertThat(detail.id()).isEqualTo(send.getId());
        assertThat(detail.templateName()).isEqualTo("합격 안내");
        assertThat(detail.mailSubject()).isEqualTo("[신영증권] #{이름}님 안내");
        assertThat(detail.mailBody()).isEqualTo("#{이름}님, 안녕하세요.");
        assertThat(detail.smsBody()).isEqualTo("#{이름}님 안내 문자");
        assertThat(detail.status()).isEqualTo(MessageSendStatus.RESULT_PENDING);
        assertThat(detail.mail()).isEqualTo(new MessageChannelCountResponse(0, 1, 2, 0, 0));
        assertThat(detail.recipients()).extracting(MessageRecipientResponse::id)
                .containsExactly(applicant.getId(), purged.getId(), tester.getId());
        MessageRecipientResponse first = detail.recipients().get(0);
        assertThat(first.applicationId()).isEqualTo(application.getId());
        assertThat(first.name()).isEqualTo("김지원");
        assertThat(first.email()).isEqualTo("kim@example.com");
        assertThat(first.phone()).isEqualTo("01000000000");
        assertThat(first.smsStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.smsFailureReason()).isEqualTo("9999");
        assertThat(first.smsKind()).isEqualTo(SmsKind.SMS);
        MessageRecipientResponse second = detail.recipients().get(1);
        assertThat(second.applicationId()).isEqualTo(application.getId());
        assertThat(second.name()).isNull();
        assertThat(second.email()).isNull();
        assertThat(second.phone()).isNull();
        assertThat(detail.recipients().get(2).applicationId()).isNull();
    }

    @Test
    void 없는_발송이면_404_예외() {
        assertThatThrownBy(() -> messageHistoryService.detail(999_999L))
                .isInstanceOf(MessageSendNotFoundException.class)
                .hasMessage("발송 기록을 찾을 수 없습니다.");
    }

    @Test
    void 페이지와_기간이_잘못되면_거부한다() {
        MessageHistoryCondition all = new MessageHistoryCondition(null, null, null, null, null, null);
        LocalDate today = LocalDate.now(clock);

        assertThatThrownBy(() -> messageHistoryService.search(all, -1, 20))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("page는 0 이상이어야 합니다.");
        assertThatThrownBy(() -> messageHistoryService.search(all, 0, 101))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("size는 1 이상 100 이하여야 합니다.");
        assertThatThrownBy(() -> messageHistoryService.search(
                new MessageHistoryCondition(today, today.minusDays(1), null, null, null, null), 0, 20))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("조회 시작일이 종료일보다 늦습니다.");
    }

    private PageResponse<MessageSendSummaryResponse> search(LocalDate from, LocalDate to, MessageType type, Boolean test) {
        return messageHistoryService.search(new MessageHistoryCondition(from, to, type, posting.getId(), test, null), 0, 20);
    }

    private Map<Long, MessageSendSummaryResponse> summariesById() {
        return search(null, null, null, null).content().stream()
                .collect(Collectors.toMap(MessageSendSummaryResponse::id, Function.identity()));
    }

    private static List<Long> ids(PageResponse<MessageSendSummaryResponse> page) {
        return page.content().stream().map(MessageSendSummaryResponse::id).toList();
    }

    private MessageSend saveSend(MessageType type, boolean test, boolean mailEnabled, boolean smsEnabled,
                                 LocalDateTime requestedAt) {
        return messageSendRepository.saveAndFlush(MessageSend.create(
                type, test, posting, null, "제출 완료", 1L, "합격 안내",
                mailEnabled, smsEnabled,
                mailEnabled ? "[신영증권] #{이름}님 안내" : null,
                mailEnabled ? "#{이름}님, 안녕하세요." : null,
                smsEnabled ? "#{이름}님 안내 문자" : null,
                "hr.kim", "김인사", 3, requestedAt));
    }

    private MessageRecipient saveRecipient(MessageSend send, JobApplication application,
                                           MessageDeliveryStatus mail, MessageDeliveryStatus sms) {
        return messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, application, "김지원", "kim@example.com", "01000000000",
                mail, reasonOf(mail), sms, reasonOf(sms), SmsKind.SMS));
    }

    private static String reasonOf(MessageDeliveryStatus status) {
        if (status == MessageDeliveryStatus.SKIPPED) {
            return MessageContacts.NO_CONTACT;
        }
        return status == MessageDeliveryStatus.FAILED ? "9999" : null;
    }

    private JobApplication submitted(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
