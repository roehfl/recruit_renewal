package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.domain.repository.MessageTemplateRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class SystemMailServiceTest {

    @Autowired
    private SystemMailService systemMailService;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private MessageTemplateRepository messageTemplateRepository;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    @Test
    void 인증_메일은_공고_없이_시스템_이력으로_남기고_원문에는_인증번호가_없다() {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-SYSTEM-1"));

        SystemMailOutcome outcome = systemMailService.send(MessageType.SIGNUP_VERIFICATION, "applicant@example.com", "",
                Map.of("인증번호", "123456"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.ACCEPTED);
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of("applicant@example.com")), eq(List.of("")));
        assertThat(mail.getValue().subject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(mail.getValue().text()).contains("인증번호: 123456");

        MessageSend send = latestSend(MessageType.SIGNUP_VERIFICATION);
        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getJobPosting()).isNull();
        assertThat(send.getSenderLoginId()).isEqualTo("SYSTEM");
        assertThat(send.getMailSubject()).doesNotContain("123456");
        assertThat(send.getMailBody()).contains("#{인증번호}").doesNotContain("123456");
        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(recipient.getJobApplication()).isNull();
        assertThat(recipient.getEmail()).isEqualTo("applicant@example.com");
        assertThat(recipient.getMailStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(recipient.getMailTransactionId()).isEqualTo("TX-SYSTEM-1");
        assertThat(recipient.getSmsStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(recipient.getSmsFailureReason()).isEqualTo(MessageContacts.CHANNEL_OFF);
    }

    @Test
    void 게이트웨이가_접수하지_않으면_FAILED로_남긴다() {
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.failure("E100"));

        SystemMailOutcome outcome = systemMailService.send(MessageType.PASSWORD_RESET, "reset@example.com", "김재설정",
                Map.of("인증번호", "654321"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.FAILED);
        MessageSend send = latestSend(MessageType.PASSWORD_RESET);
        MessageRecipient recipient = messageRecipientRepository.findByMessageSendIdOrderByIdAsc(send.getId()).get(0);
        assertThat(recipient.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(recipient.getMailFailureReason()).isEqualTo("E100");
    }

    @Test
    void 기본_템플릿이_없으면_보내지_않는다() {
        messageTemplateRepository.deleteAll(messageTemplateRepository.findByType(MessageType.PASSWORD_RESET));

        SystemMailOutcome outcome = systemMailService.send(MessageType.PASSWORD_RESET, "reset@example.com", "김재설정",
                Map.of("인증번호", "654321"), null, null);

        assertThat(outcome).isEqualTo(SystemMailOutcome.NO_TEMPLATE);
        verifyNoInteractions(mailGateway);
    }

    private MessageSend latestSend(MessageType type) {
        return messageSendRepository.findAll().stream()
                .filter(send -> send.getType() == type)
                .max(Comparator.comparing(MessageSend::getId))
                .orElseThrow();
    }
}
