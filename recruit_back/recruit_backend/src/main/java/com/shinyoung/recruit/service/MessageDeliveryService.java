package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 발송 단위 1개를 게이트웨이로 보내고 접수 결과를 정규화한다. DB 를 건드리지 않는다(기록은 MessageDispatchRecorder). */
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryService.class);
    /** MessageRecipient.mailTransactionId·smsTransactionId 컬럼 길이. */
    private static final int TRANSACTION_ID_MAX_LENGTH = 100;

    private final MailGateway mailGateway;
    private final SmsGateway smsGateway;
    private final MessageMailLayout messageMailLayout;
    private final MessageProperties messageProperties;

    public GatewayResult deliver(DeliveryUnit unit) {
        try {
            GatewayResult result = unit.channel() == MessageChannel.MAIL ? sendMail(unit) : sendSms(unit);
            return normalize(unit, result);
        } catch (RuntimeException e) {
            // 예외 메시지에는 주소가 섞일 수 있어 남기지 않는다.
            log.warn("메시지 게이트웨이 호출 실패: channel={}, recipients={}, error={}",
                    unit.channel(), unit.recipientIds().size(), e.getClass().getSimpleName());
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
    }

    /**
     * null 결과·사유 없는 실패는 GATEWAY_ERROR 실패로 바꾼다. 접수했는데 거래 ID 가 비었거나 컬럼보다 길면
     * 결과를 매칭할 수 없으므로 GATEWAY_ERROR 실패로 바꾸고 경고를 남긴다(연락처는 로그에 남기지 않는다).
     */
    private static GatewayResult normalize(DeliveryUnit unit, GatewayResult result) {
        if (result == null) {
            return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
        }
        if (result.accepted()) {
            String transactionId = result.transactionId();
            if (transactionId == null || transactionId.isBlank() || transactionId.length() > TRANSACTION_ID_MAX_LENGTH) {
                log.warn("메시지 게이트웨이가 쓸 수 없는 거래 ID 로 접수했습니다: channel={}, recipients={}",
                        unit.channel(), unit.recipientIds().size());
                return GatewayResult.failure(MessageContacts.GATEWAY_ERROR);
            }
            return result;
        }
        boolean noReason = result.failureReason() == null || result.failureReason().isBlank();
        return noReason ? GatewayResult.failure(MessageContacts.GATEWAY_ERROR) : result;
    }

    private GatewayResult sendMail(DeliveryUnit unit) {
        String html = messageMailLayout.render(unit.subject(), unit.body());
        MailMessage message = new MailMessage(
                messageProperties.getSenderName(), messageProperties.getSenderEmail(), unit.subject(), html, unit.body());
        return mailGateway.send(message, unit.to(), unit.names());
    }

    private GatewayResult sendSms(DeliveryUnit unit) {
        SmsMessage message = new SmsMessage(messageProperties.getSmsCallbackNumber(), unit.body(), unit.smsKind());
        return smsGateway.send(message, unit.to(), unit.names());
    }
}
