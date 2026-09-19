package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 발송 결과 수신 처리부(설계서 7.4). 결과를 보관한 뒤 반영을 시도하고, 반영됐거나 이미 기록된 거래면 보관에서 뺀다.
 * 트랜잭션은 MessageDispatchRecorder 가 연다(이 빈에는 없다). 로그에는 거래 ID 만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryReportHandler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReportHandler.class);
    /** 짝을 못 찾은 결과를 보관하는 최대 시간. */
    static final Duration BUFFER_TTL = Duration.ofMinutes(10);

    private final MessageDispatchRecorder messageDispatchRecorder;
    private final DeliveryReportBuffer deliveryReportBuffer;
    private final Clock clock;

    /** 소켓 클라이언트(목업은 MockDeliveryReportScheduler)가 결과 1건마다 부른다. */
    public void handle(DeliveryReport report) {
        if (report == null || report.transactionId() == null || report.transactionId().isBlank()) {
            log.warn("거래 ID 가 없는 발송 결과를 무시합니다.");
            return;
        }
        deliveryReportBuffer.put(report);
        tryApply(report);
    }

    /** 디스패처가 단위 접수를 기록한 직후 부른다. 먼저 도착해 보관 중인 결과가 있으면 반영한다. */
    public void applyBuffered(String transactionId) {
        deliveryReportBuffer.find(transactionId).ifPresent(this::tryApply);
    }

    /** 1분마다 보관 결과를 다시 시도하고, 받은 지 10분이 지나도 짝이 없으면 버린다. */
    @Scheduled(fixedDelay = 60000)
    public void retryBuffered() {
        LocalDateTime expiredBefore = LocalDateTime.now(clock).minus(BUFFER_TTL);
        for (DeliveryReportBuffer.Entry entry : deliveryReportBuffer.entries()) {
            if (!tryApply(entry.report()) && entry.receivedAt().isBefore(expiredBefore)) {
                deliveryReportBuffer.remove(entry.report().transactionId());
                log.warn("발송 결과와 맞는 수신자가 10분 동안 없어 버립니다: transactionId={}",
                        entry.report().transactionId());
            }
        }
    }

    private boolean tryApply(DeliveryReport report) {
        try {
            if (messageDispatchRecorder.applyReport(report)) {
                deliveryReportBuffer.remove(report.transactionId());
                return true;
            }
        } catch (RuntimeException e) {
            log.warn("발송 결과 반영 실패: transactionId={}, error={}",
                    report.transactionId(), e.getClass().getSimpleName());
        }
        return false;
    }
}
