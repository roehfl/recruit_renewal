package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.request.MessageTestSendRequest;
import com.shinyoung.recruit.dto.request.MessageTesterRequest;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResponse;
import com.shinyoung.recruit.dto.response.MessageTestSendResultResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.SmsKind;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.InvalidMessageException;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
@RecordApplicationEvents
class MessageSendServiceTest {

    private static final CustomUserDetails HR = CustomUserDetails.fromLdap(
            "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));

    @Autowired
    private MessageSendService messageSendService;
    @Autowired
    private MessageProperties messageProperties;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private DeliveryReportBuffer deliveryReportBuffer;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private JobPosting posting;

    @BeforeEach
    void setUp() {
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-MAIL-1"));
        when(smsGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-SMS-1"));
    }

    @Test
    void 발송은_선택_수신자만_기록하고_치환_결과를_이벤트로_넘긴다() {
        JobApplication kim = submitted("김민준");
        JobApplication lee = submitted("이서연");
        basicInfo(lee, "이서연", "010-12", "lee@example.com");
        submitted("박제외");

        MessageSendResultResponse result = messageSendService.send(sendRequest(
                List.of(kim.getId(), lee.getId(), 999_999L),
                content("[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자")), HR);

        assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
        assertThat(result.recipientCount()).isEqualTo(2);
        assertThat(result.excludedCount()).isEqualTo(1);
        MessageSend send = messageSendRepository.findById(result.sendId()).orElseThrow();
        assertThat(send.isTest()).isFalse();
        assertThat(send.getSenderLoginId()).isEqualTo("hr.kim");
        assertThat(send.getSenderName()).isEqualTo("김인사");
        assertThat(send.getConditionSummary()).isEqualTo("제출 완료");
        assertThat(send.getMailSubject()).isEqualTo("[신영증권] #{이름}님 안내");
        List<MessageRecipient> recipients = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId());
        assertThat(recipients).hasSize(2);
        assertThat(recipients.get(1).getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipients.get(1).getSmsFailureReason()).isEqualTo(MessageContacts.INVALID_CONTACT);
        MessageSendRequestedEvent event = applicationEvents.stream(MessageSendRequestedEvent.class).findFirst().orElseThrow();
        assertThat(event.messageSendId()).isEqualTo(send.getId());
        assertThat(event.items()).filteredOn(item -> item.channel() == MessageChannel.MAIL)
                .extracting(DeliveryItem::name, DeliveryItem::subject)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("김민준", "[신영증권] 김민준님 안내"),
                        org.assertj.core.groups.Tuple.tuple("이서연", "[신영증권] 이서연님 안내"));
        assertThat(event.items()).filteredOn(item -> item.channel() == MessageChannel.SMS)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.body()).isEqualTo("김민준님 안내 문자");
                    assertThat(item.name()).isEqualTo("김민준");
                    assertThat(item.to()).isEqualTo("01000000000");
                    assertThat(item.smsKind()).isEqualTo(SmsKind.SMS);
                });
        org.mockito.Mockito.verifyNoInteractions(mailGateway, smsGateway);
    }

    @Test
    void 끈_채널은_CHANNEL_OFF로_제외하고_원문도_저장하지_않는다() {
        JobApplication kim = submitted("김민준");

        MessageSendResultResponse result = messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, true, false, "제목", "본문", "무시될 문자")), HR);

        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(result.sendId()).get(0);
        assertThat(recipient.getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipient.getSmsFailureReason()).isEqualTo(MessageContacts.CHANNEL_OFF);
        assertThat(messageSendRepository.findById(result.sendId()).orElseThrow().getSmsBody()).isNull();
    }

    @Test
    void 채널을_모두_끄면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, false, false, null, null, null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일이나 SMS 중 하나 이상 켜야 합니다.");
    }

    @Test
    void 켠_메일에_제목이나_본문이_없으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, true, false, "제목", " ", null)), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("메일 제목과 본문을 입력해야 합니다.");
    }

    @Test
    void 허용되지_않은_변수가_있으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                content("제목", "#{면접일시}에 오세요", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("사용할 수 없는 변수: #{면접일시}");
    }

    @Test
    void 선택한_수신자가_조건에_없으면_거부한다() {
        submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(999_999L),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("보낼 수신자가 없습니다. 대상자를 다시 조회하세요.");
    }

    @Test
    void 최대_인원을_넘으면_거부한다() {
        JobApplication kim = submitted("김민준");
        JobApplication lee = submitted("이서연");
        int original = messageProperties.getMaxRecipients();
        ReflectionTestUtils.setField(messageProperties, "maxRecipients", 1);
        try {
            assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId(), lee.getId()),
                    content("제목", "본문", "문자")), HR))
                    .isInstanceOf(InvalidMessageException.class)
                    .hasMessage("한 번에 최대 1명까지 보낼 수 있습니다.");
        } finally {
            ReflectionTestUtils.setField(messageProperties, "maxRecipients", original);
        }
    }

    @Test
    void SMS가_2000byte를_넘는_수신자가_있으면_수험번호로_알리고_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                new MessageContentRequest(null, false, true, null, null, "가".repeat(1000) + "#{이름}")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("SMS가 2,000byte를 넘는 수신자가 1명 있습니다(수험번호 " + kim.getId() + ").");
    }

    @Test
    void 보낼_수_있는_연락처가_없으면_거부한다() {
        JobApplication kim = submitted("김민준");
        basicInfo(kim, "김민준", "010-12", "not-an-email");

        assertThatThrownBy(() -> messageSendService.send(sendRequest(List.of(kim.getId()),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("보낼 수 있는 연락처가 없습니다.");
    }

    @Test
    void 테스트_발송은_미리보기_대상_값으로_즉시_보내고_접수_결과를_준다() {
        JobApplication kim = submitted("김민준");

        MessageTestSendResponse response = messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, JobApplicationStatus.SUBMITTED,
                kim.getId(),
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", "010-0000-1234")),
                content("[신영증권] #{이름}님 안내", "본문", "#{이름}님 문자")), HR);

        assertThat(response.results()).extracting(MessageTestSendResultResponse::channel, MessageTestSendResultResponse::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.MAIL, MessageDeliveryStatus.REQUESTED),
                        org.assertj.core.groups.Tuple.tuple(MessageChannel.SMS, MessageDeliveryStatus.REQUESTED));
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), org.mockito.ArgumentMatchers.eq(List.of("hr.kim@example.com")),
                org.mockito.ArgumentMatchers.eq(List.of("김인사")));
        assertThat(mail.getValue().subject()).isEqualTo("[테스트] [신영증권] 김민준님 안내");
        ArgumentCaptor<SmsMessage> sms = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsGateway).send(sms.capture(), org.mockito.ArgumentMatchers.eq(List.of("01000001234")),
                org.mockito.ArgumentMatchers.eq(List.of("김인사")));
        assertThat(sms.getValue().body()).isEqualTo("[테스트] 김민준님 문자");
        MessageSend send = messageSendRepository.findById(response.sendId()).orElseThrow();
        assertThat(send.isTest()).isTrue();
        MessageRecipient tester = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(tester.getJobApplication()).isNull();
        assertThat(tester.getMailTransactionId()).isEqualTo("TX-MAIL-1");
        assertThat(tester.getSmsTransactionId()).isEqualTo("TX-SMS-1");
    }

    @Test
    void 테스트_발송_중에_먼저_온_결과가_반영되면_바로_SENT로_읽힌다() {
        JobApplication kim = submitted("김민준");
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("T-EARLY"));
        deliveryReportBuffer.put(new DeliveryReport(MessageChannel.MAIL, "T-EARLY", "hr.kim@example.com", "00"));

        MessageTestSendResponse response = messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, JobApplicationStatus.SUBMITTED,
                kim.getId(),
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", null)),
                new MessageContentRequest(null, true, false, "[신영증권] #{이름}님 안내", "본문", null)), HR);

        assertThat(response.results()).filteredOn(result -> result.channel() == MessageChannel.MAIL)
                .singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo(MessageDeliveryStatus.SENT));
        assertThat(deliveryReportBuffer.find("T-EARLY")).isEmpty();
    }

    @Test
    void 테스트_발송도_SMS가_2000byte를_넘으면_미리보기_수험번호로_알린다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", null, "010-0000-1234")),
                new MessageContentRequest(null, false, true, null, null, "가".repeat(1000) + "#{이름}")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("SMS가 2,000byte를 넘습니다(미리보기 수험번호 " + kim.getId() + ").");
    }

    @Test
    void 테스트_수신자에_연락처가_하나도_없으면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", " ", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("테스트 수신자는 이메일이나 휴대폰 중 하나 이상 입력해야 합니다.");
    }

    @Test
    void 테스트_수신자_연락처_형식이_틀리면_거부한다() {
        JobApplication kim = submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, kim.getId(),
                List.of(new MessageTesterRequest("김인사", "not-an-email", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("테스트 수신자 연락처 형식이 올바르지 않습니다.");
    }

    @Test
    void 미리보기_대상이_조건에_없으면_테스트_발송을_거부한다() {
        submitted("김민준");

        assertThatThrownBy(() -> messageSendService.testSend(new MessageTestSendRequest(
                MessageType.FREE, posting.getId(), null, null, null, null, 999_999L,
                List.of(new MessageTesterRequest("김인사", "hr.kim@example.com", null)),
                content("제목", "본문", "문자")), HR))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("미리보기 대상이 현재 조건의 대상자가 아닙니다. 대상자를 다시 조회하세요.");
    }

    private MessageSendRequest sendRequest(List<Long> applicationIds, MessageContentRequest content) {
        return new MessageSendRequest(MessageType.FREE, posting.getId(), null, null, null,
                JobApplicationStatus.SUBMITTED, applicationIds, content);
    }

    private MessageContentRequest content(String mailSubject, String mailBody, String smsBody) {
        return new MessageContentRequest(null, true, true, mailSubject, mailBody, smsBody);
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

    private void basicInfo(JobApplication application, String name, String phone, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), phone, null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }
}
