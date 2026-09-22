package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageDispatchRecorderTest {

    @Autowired
    private MessageDispatchRecorder recorder;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;

    @Test
    void 접수된_단위는_REQUESTED와_거래_ID를_접수_실패한_단위는_FAILED와_사유를_기록한다() {
        MessageSend send = saveSend(3);
        MessageRecipient first = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient second = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        MessageRecipient third = saveRecipient(send, MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);

        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.failure("SMTP_REJECTED"));
        recorder.recordUnit(unit(MessageChannel.SMS, first.getId(), third.getId()), GatewayResult.accepted("TX-SMS-1"));

        assertThat(first.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.getMailFailureReason()).isEqualTo("SMTP_REJECTED");
        assertThat(first.getMailTransactionId()).isNull();
        assertThat(second.getMailStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(first.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(first.getSmsTransactionId()).isEqualTo("TX-SMS-1");
        assertThat(third.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(third.getSmsTransactionId()).isEqualTo("TX-SMS-1");
        assertThat(third.getMailStatus()).isEqualTo(MessageDeliveryStatus.SKIPPED);
        assertThat(first.getProcessedAt()).isNotNull();
    }

    @Test
    void 긴_실패_사유는_200자로_자른다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);

        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.failure("x".repeat(300)));

        assertThat(recipient.getMailFailureReason()).hasSize(200);
    }

    @Test
    void 결과는_그_채널_거래에서_연락처가_같은_수신자만_바꾸고_성공_코드면_SENT로_둔다() {
        MessageSend send = saveSend(2);
        MessageRecipient kim = saveRecipient(send, "kim@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient lee = saveRecipient(send, "lee@example.com", "01000000001",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, kim.getId(), lee.getId()), GatewayResult.accepted("TX-1"));
        recorder.recordUnit(unit(MessageChannel.SMS, kim.getId()), GatewayResult.accepted("TX-1"));

        boolean known = recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-1", " KIM@example.com ", "00"));

        assertThat(known).isTrue();
        MessageRecipient reloadedKim = messageRecipientRepository.findById(kim.getId()).orElseThrow();
        assertThat(reloadedKim.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloadedKim.getMailFailureReason()).isNull();
        assertThat(reloadedKim.getSmsStatus()).as("같은 거래 ID 라도 SMS 채널은 바꾸지 않는다")
                .isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(messageRecipientRepository.findById(lee.getId()).orElseThrow().getMailStatus())
                .isEqualTo(MessageDeliveryStatus.REQUESTED);
    }

    @Test
    void 성공_코드가_아니면_FAILED로_두고_결과코드를_사유로_남긴다_SMS는_숫자만_비교한다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, "kim@example.com", "010-1234-5678",
                MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);
        recorder.recordUnit(unit(MessageChannel.SMS, recipient.getId()), GatewayResult.accepted("TX-SMS-2"));

        recorder.applyReport(new DeliveryReport(MessageChannel.SMS, "TX-SMS-2", "01012345678", "E1"));

        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getSmsStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(reloaded.getSmsFailureReason()).isEqualTo("E1");
        assertThat(reloaded.getSmsTransactionId()).isEqualTo("TX-SMS-2");
    }

    @Test
    void 이미_반영된_수신자의_결과가_다시_오면_바꾸지_않고_아는_거래로_본다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, "kim@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.accepted("TX-MAIL-3"));
        recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-MAIL-3", "kim@example.com", "00"));

        boolean known = recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-MAIL-3", "kim@example.com", "99"));

        assertThat(known).isTrue();
        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloaded.getMailFailureReason()).isNull();
    }

    @Test
    void 연락처가_맞는_수신자가_없으면_아무것도_바꾸지_않고_버린다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, "kim@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.accepted("TX-MAIL-4"));

        boolean known = recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-MAIL-4", "other@example.com", "00"));

        assertThat(known).isTrue();
        assertThat(messageRecipientRepository.findById(recipient.getId()).orElseThrow().getMailStatus())
                .isEqualTo(MessageDeliveryStatus.REQUESTED);
    }

    @Test
    void 같은_연락처가_한_거래에_두_번_있으면_결과_한_줄은_한_명에게만_반영한다() {
        MessageSend send = saveSend(2);
        MessageRecipient first = saveRecipient(send, "kim@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        MessageRecipient second = saveRecipient(send, "kim@example.com", "01000000000",
                MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.accepted("TX-MAIL-5"));

        recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-MAIL-5", "kim@example.com", "00"));
        recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-MAIL-5", "kim@example.com", "99"));

        assertThat(messageRecipientRepository.findAllById(List.of(first.getId(), second.getId())))
                .extracting(MessageRecipient::getMailStatus)
                .containsExactlyInAnyOrder(MessageDeliveryStatus.SENT, MessageDeliveryStatus.FAILED);
    }

    @Test
    void 기록되지_않은_거래_ID면_모른다고_답한다() {
        assertThat(recorder.applyReport(new DeliveryReport(MessageChannel.MAIL, "TX-UNKNOWN", "kim@example.com", "00")))
                .isFalse();
    }

    private DeliveryUnit unit(MessageChannel channel, Long... recipientIds) {
        return new DeliveryUnit(channel, null, "본문", channel == MessageChannel.SMS ? SmsKind.SMS : null,
                List.of(recipientIds), List.of(), List.of());
    }

    private MessageSend saveSend(int recipientCount) {
        JobPosting posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        jobPostingRepository.saveAndFlush(posting);
        return messageSendRepository.saveAndFlush(MessageSend.create(
                MessageType.FREE, false, posting, null, "제출 완료", null, null,
                true, true, "제목", "본문", "문자", "hr.kim", "김인사",
                recipientCount, LocalDateTime.of(2026, 9, 19, 10, 0)));
    }

    private MessageRecipient saveRecipient(MessageSend send, MessageDeliveryStatus mail, MessageDeliveryStatus sms) {
        return saveRecipient(send, "kim@example.com", "01000000000", mail, sms);
    }

    private MessageRecipient saveRecipient(MessageSend send, String email, String phone,
                                           MessageDeliveryStatus mail, MessageDeliveryStatus sms) {
        return messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, null, "김지원", email, phone,
                mail, mail == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                sms, sms == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                SmsKind.SMS));
    }
}
