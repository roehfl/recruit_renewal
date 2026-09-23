package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 목업 메일 게이트웨이. 항상 접수(가짜 거래 ID)하고 3초 뒤 목업 결과를 예약한다(MockDeliveryReportScheduler).
 * 거래 ID·마스킹한 수신자·길이를 로그로 남긴다.
 * 로컬 목업이라 본문(text)도 남긴다(인증번호 확인용). 운영 게이트웨이(trnode)는 본문을 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailGateway.class);

    private final MockDeliveryReportScheduler mockDeliveryReportScheduler;

    @Override
    public GatewayResult send(MailMessage message, List<String> toAddresses, List<String> names) {
        String transactionId = UUID.randomUUID().toString();
        log.info("[message-mail] transactionId={} to={} subjectLength={} htmlLength={}",
                transactionId,
                toAddresses.stream().map(MessageContacts::maskEmail).toList(),
                message.subject().length(),
                message.html().length());
        log.info("[message-mail] transactionId={} text={}", transactionId, message.text());
        mockDeliveryReportScheduler.schedule(MessageChannel.MAIL, transactionId, toAddresses);
        return GatewayResult.accepted(transactionId);
    }
}
