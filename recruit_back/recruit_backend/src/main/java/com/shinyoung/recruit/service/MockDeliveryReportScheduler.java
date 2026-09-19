package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 목업 게이트웨이(logging)의 가짜 발송 결과(설계서 7.4). 접수 3초 뒤 TaskScheduler 로 DeliveryReportHandler 에 넘긴다.
 * 결과코드는 0000, 그 단위 수신 연락처에 fail 이 들어 있으면 9999(화면 확인용). 실제 솔루션 연동 시에는 뜨지 않는다.
 * TaskScheduler 는 SchedulingConfig(@EnableScheduling)가 있을 때 부트가 만드는 기본 taskScheduler(스레드 1개)다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "logging", matchIfMissing = true)
public class MockDeliveryReportScheduler {

    static final Duration REPORT_DELAY = Duration.ofSeconds(3);
    static final String SUCCESS_CODE = "0000";
    static final String FAILURE_CODE = "9999";

    private final TaskScheduler taskScheduler;
    private final DeliveryReportHandler deliveryReportHandler;
    private final Clock clock;

    public void schedule(String transactionId, List<String> recipients) {
        String resultCode = recipients.stream().anyMatch(MockDeliveryReportScheduler::containsFail)
                ? FAILURE_CODE
                : SUCCESS_CODE;
        DeliveryReport report = new DeliveryReport(transactionId, resultCode);
        taskScheduler.schedule(() -> deliveryReportHandler.handle(report), clock.instant().plus(REPORT_DELAY));
    }

    private static boolean containsFail(String recipient) {
        return recipient != null && recipient.toLowerCase(Locale.ROOT).contains("fail");
    }
}
