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
    void 결과는_거래_ID가_같고_REQUESTED인_채널만_바꾸고_성공_코드면_SENT로_둔다() {
        MessageSend send = saveSend(2);
        MessageRecipient first = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.PENDING);
        MessageRecipient second = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, first.getId(), second.getId()), GatewayResult.accepted("TX-MAIL-1"));
        recorder.recordUnit(unit(MessageChannel.SMS, first.getId()), GatewayResult.accepted("TX-SMS-1"));

        boolean known = recorder.applyReport(new DeliveryReport("TX-MAIL-1", "0000"));

        assertThat(known).isTrue();
        MessageRecipient reloadedFirst = messageRecipientRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloadedFirst.getMailFailureReason()).isNull();
        assertThat(reloadedFirst.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
        assertThat(messageRecipientRepository.findById(second.getId()).orElseThrow().getMailStatus())
                .isEqualTo(MessageDeliveryStatus.SENT);
    }

    @Test
    void 성공_코드가_아니면_FAILED로_두고_결과코드를_사유로_남긴다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.SKIPPED, MessageDeliveryStatus.PENDING);
        recorder.recordUnit(unit(MessageChannel.SMS, recipient.getId()), GatewayResult.accepted("TX-SMS-2"));

        recorder.applyReport(new DeliveryReport("TX-SMS-2", "E102"));

        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getSmsStatus()).isEqualTo(MessageDeliveryStatus.FAILED);
        assertThat(reloaded.getSmsFailureReason()).isEqualTo("E102");
        assertThat(reloaded.getSmsTransactionId()).isEqualTo("TX-SMS-2");
    }

    @Test
    void 이미_반영된_거래의_결과가_다시_오면_바꾸지_않고_아는_거래로_본다() {
        MessageSend send = saveSend(1);
        MessageRecipient recipient = saveRecipient(send, MessageDeliveryStatus.PENDING, MessageDeliveryStatus.SKIPPED);
        recorder.recordUnit(unit(MessageChannel.MAIL, recipient.getId()), GatewayResult.accepted("TX-MAIL-3"));
        recorder.applyReport(new DeliveryReport("TX-MAIL-3", "0000"));

        boolean known = recorder.applyReport(new DeliveryReport("TX-MAIL-3", "9999"));

        assertThat(known).isTrue();
        MessageRecipient reloaded = messageRecipientRepository.findById(recipient.getId()).orElseThrow();
        assertThat(reloaded.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloaded.getMailFailureReason()).isNull();
    }

    @Test
    void 기록되지_않은_거래_ID면_모른다고_답한다() {
        assertThat(recorder.applyReport(new DeliveryReport("TX-UNKNOWN", "0000"))).isFalse();
    }

    private DeliveryUnit unit(MessageChannel channel, Long... recipientIds) {
        return new DeliveryUnit(channel, null, "본문", channel == MessageChannel.SMS ? SmsKind.SMS : null,
                List.of(recipientIds), List.of());
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
        return messageRecipientRepository.saveAndFlush(MessageRecipient.create(
                send, null, "김지원", "kim@example.com", "01000000000",
                mail, mail == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                sms, sms == MessageDeliveryStatus.SKIPPED ? MessageContacts.NO_CONTACT : null,
                SmsKind.SMS));
    }
}
