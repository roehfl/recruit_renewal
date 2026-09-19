package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 목업 문자 게이트웨이. 항상 접수(가짜 거래 ID)하고 3초 뒤 목업 결과를 예약한다(MockDeliveryReportScheduler).
 * 거래 ID·마스킹한 번호·구분·길이만 로그로 남긴다(본문 금지).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    private final MockDeliveryReportScheduler mockDeliveryReportScheduler;

    @Override
    public GatewayResult send(SmsMessage message, List<String> toNumbers) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-sms] transactionId={} to={} kind={} bodyLength={}",
                transactionId,
                toNumbers.stream().map(MessageContacts::maskPhone).toList(),
                message.kind(),
                message.body().length());
        mockDeliveryReportScheduler.schedule(transactionId, toNumbers);
        return GatewayResult.accepted(transactionId);
    }
}
