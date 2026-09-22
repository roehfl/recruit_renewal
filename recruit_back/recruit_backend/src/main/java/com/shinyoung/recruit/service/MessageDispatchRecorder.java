package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MessageDispatchRecorder.class);
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
     * 발송 결과 1건(수신자 1명)을 반영한다. 그 채널 거래 ID 의 수신자 중 연락처가 같고 아직 REQUESTED 인 한 명만 바꾼다.
     * 이미 반영된 행은 바뀌지 않는다(멱등). 결과코드가 성공 목록(recruit.message.success-result-codes)에 있으면 SENT,
     * 아니면 FAILED + 사유 = 결과코드. 연락처가 맞는 수신자가 없으면(파기 등) 채널·거래 ID 만 경고로 남기고 버린다.
     *
     * @return 거래 ID 를 알면 true. false 면 아직 접수 기록 전이라 보관해야 한다.
     */
    public boolean applyReport(DeliveryReport report) {
        MessageChannel channel = report.channel();
        // 수신자를 먼저 읽고 id 로 update 한다. 접수 기록이 아직 커밋 전이면(READ COMMITTED) 빈 목록이라
        // 보관해 두고 applyBuffered/retryBuffered 로 나중에 다시 반영한다.
        List<MessageRecipient> candidates = channel == MessageChannel.MAIL
                ? messageRecipientRepository.findByMailTransactionId(report.transactionId())
                : messageRecipientRepository.findBySmsTransactionId(report.transactionId());
        if (candidates.isEmpty()) {
            return false;
        }
        List<Long> matchedIds = candidates.stream()
                .filter(recipient -> sameContact(channel, recipient, report.to()))
                .map(MessageRecipient::getId)
                .toList();
        if (matchedIds.isEmpty()) {
            log.warn("발송 결과와 연락처가 맞는 수신자가 없어 버립니다: channel={}, transactionId={}",
                    channel, report.transactionId());
            return true;
        }
        boolean success = messageProperties.getSuccessResultCodes().contains(report.resultCode());
        MessageDeliveryStatus status = success ? MessageDeliveryStatus.SENT : MessageDeliveryStatus.FAILED;
        String reason = success ? null : truncate(report.resultCode());
        LocalDateTime now = LocalDateTime.now(clock);
        // 같은 연락처가 한 거래에 두 번 들어간 경우 결과 한 줄은 아직 REQUESTED 인 첫 행에만 반영한다.
        for (Long recipientId : matchedIds) {
            int updated = channel == MessageChannel.MAIL
                    ? messageRecipientRepository.applyMailReport(recipientId, status, reason, now)
                    : messageRecipientRepository.applySmsReport(recipientId, status, reason, now);
            if (updated > 0) {
                break;
            }
        }
        return true;
    }

    /** 메일은 앞뒤 공백을 뺀 주소를 대소문자 없이, SMS 는 숫자만 남긴 번호를 비교한다. */
    private static boolean sameContact(MessageChannel channel, MessageRecipient recipient, String to) {
        if (channel == MessageChannel.MAIL) {
            String email = MessageContacts.normalizeEmail(recipient.getEmail());
            return email != null && email.equalsIgnoreCase(MessageContacts.normalizeEmail(to));
        }
        String phone = MessageContacts.normalizePhone(recipient.getPhone());
        return phone != null && !phone.isEmpty() && phone.equals(MessageContacts.normalizePhone(to));
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
