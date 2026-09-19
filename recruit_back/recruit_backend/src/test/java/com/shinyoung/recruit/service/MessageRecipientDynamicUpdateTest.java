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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DynamicUpdate} 회귀 테스트(Task 1 리뷰 보완, 설계서 7.4): 한 채널의 결과 반영(bulk update, 별도
 * 트랜잭션)과 다른 채널의 접수 기록(entity dirty check)이 같은 행에서 겹쳐도 서로 덮어쓰지 않아야 한다.
 *
 * <p>{@code @Transactional} 롤백이 별도 트랜잭션의 커밋을 가리므로(같은 이유로 {@link MessageSendAsyncFlowTest}도
 * 쓰지 않는다) 이 테스트는 {@code @Transactional}을 쓰지 않는다. 만든 행은 {@link #tearDown()}에서 지운다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageRecipientDynamicUpdateTest {

    @Autowired
    private MessageDispatchRecorder recorder;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private JobPosting posting;
    private MessageSend send;
    private Long recipientId;

    @AfterEach
    void tearDown() {
        TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
        cleanup.executeWithoutResult(status -> {
            if (recipientId != null) {
                messageRecipientRepository.findById(recipientId).ifPresent(messageRecipientRepository::delete);
            }
            if (send != null) {
                messageSendRepository.findById(send.getId()).ifPresent(messageSendRepository::delete);
            }
            if (posting != null) {
                jobPostingRepository.findById(posting.getId()).ifPresent(jobPostingRepository::delete);
            }
        });
    }

    @Test
    void 결과_반영_트랜잭션과_다른_채널_접수_기록이_겹쳐도_먼저_반영된_결과를_덮어쓰지_않는다() {
        TransactionTemplate setup = new TransactionTemplate(transactionManager);
        setup.executeWithoutResult(status -> {
            posting = JobPosting.create("동시성 공고", "Content",
                    LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
            posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
            posting = jobPostingRepository.saveAndFlush(posting);
            send = messageSendRepository.saveAndFlush(MessageSend.create(
                    MessageType.FREE, false, posting, null, "제출 완료", null, null,
                    true, true, "제목", "본문", "문자", "hr.kim", "김인사",
                    1, LocalDateTime.of(2026, 9, 19, 10, 0)));
            MessageRecipient recipient = MessageRecipient.create(
                    send, null, "김지원", "kim@example.com", "01000000000",
                    MessageDeliveryStatus.PENDING, null,
                    MessageDeliveryStatus.PENDING, null,
                    SmsKind.SMS);
            recipient.recordRequested(MessageChannel.MAIL, "T-MAIL", LocalDateTime.of(2026, 9, 19, 10, 0));
            recipient = messageRecipientRepository.saveAndFlush(recipient);
            recipientId = recipient.getId();
        });

        TransactionTemplate tx1 = new TransactionTemplate(transactionManager);
        tx1.executeWithoutResult(status -> {
            MessageRecipient recipient = messageRecipientRepository.findById(recipientId).orElseThrow();

            TransactionTemplate tx2 = new TransactionTemplate(transactionManager);
            tx2.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx2.executeWithoutResult(innerStatus ->
                    assertThat(recorder.applyReport(new DeliveryReport("T-MAIL", "0000"))).isTrue());

            recipient.recordRequested(MessageChannel.SMS, "T-SMS", LocalDateTime.of(2026, 9, 19, 10, 5));
        });

        MessageRecipient reloaded = messageRecipientRepository.findById(recipientId).orElseThrow();
        assertThat(reloaded.getMailStatus()).isEqualTo(MessageDeliveryStatus.SENT);
        assertThat(reloaded.getSmsStatus()).isEqualTo(MessageDeliveryStatus.REQUESTED);
    }
}
