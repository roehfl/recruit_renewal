package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 단위의 접수 결과와 나중에 오는 발송 결과를 수신자에 기록한다(설계서 7.1·7.4). 기본 전파(REQUIRED):
 * 비동기 디스패처·결과 처리부에서는 호출마다 새 트랜잭션, 테스트 발송(동기)에서는 요청 트랜잭션에 합류한다.
 * 트랜잭션 경계를 이 빈에 모아 DeliveryReportHandler 의 자기 호출 문제를 피한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MessageDispatchRecorder {

    private static final int FAILURE_REASON_MAX_LENGTH = 200;

    private final MessageRecipientRepository messageRecipientRepository;
    private final MessageProperties messageProperties;
    private final Clock clock;

    /** 단위의 접수 결과를 그 단위 수신자 전원에 적용한다. 접수되면 REQUESTED + 거래 ID, 아니면 FAILED + 사유. */
    public void recordUnit(DeliveryUnit unit, GatewayResult result) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MessageRecipient> recipients = messageRecipientRepository.findAllById(unit.recipientIds());
        if (result.accepted()) {
            recipients.forEach(recipient -> recipient.recordRequested(unit.channel(), result.transactionId(), now));
            return;
        }
        String reason = truncate(result.failureReason());
        recipients.forEach(recipient ->
                recipient.recordResult(unit.channel(), MessageDeliveryStatus.FAILED, reason, now));
    }

    /**
     * 발송 결과 1건을 거래 ID 가 같고 아직 REQUESTED 인 수신자·채널에 반영한다. 이미 반영된 행은 바뀌지 않는다(멱등).
     * 결과코드가 성공 목록(recruit.message.success-result-codes)에 있으면 SENT, 아니면 FAILED + 사유 = 결과코드.
     *
     * @return 거래 ID 를 알면(이번에 바꾼 행이 있거나 이미 기록된 거래) true. false 면 아직 접수 기록 전이라 보관해야 한다.
     */
    public boolean applyReport(DeliveryReport report) {
        boolean success = messageProperties.getSuccessResultCodes().contains(report.resultCode());
        MessageDeliveryStatus status = success ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.FAILED;
        String reason = success ? null : truncate(report.resultCode());
        LocalDateTime now = LocalDateTime.now(clock);
        String transactionId = report.transactionId();
        // 존재 확인을 update 전에 한다: update 뒤에 확인하면, 그 사이(READ COMMITTED) 접수 기록이 막 커밋돼
        // update 는 0행(REQUESTED 상태를 아직 못 봄)인데 확인은 true 가 되는 경우 이미 기록된 거래로 오판해
        // 버퍼에서 빠지고 결과가 영영 반영되지 않는다. 존재하지 않으면(false) 아직 접수 전이라 보관해 두고
        // applyBuffered/retryBuffered 로 나중에 다시 반영한다.
        boolean recorded = messageRecipientRepository.existsByMailTransactionIdOrSmsTransactionId(transactionId, transactionId);
        int updated = messageRecipientRepository.applyMailReport(transactionId, status, reason, now)
                + messageRecipientRepository.applySmsReport(transactionId, status, reason, now);
        return updated > 0 || recorded;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
