package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화(Phase 09f-4 — 프로젝트 최초 도입). 사용처: {@code ClientEventLogCleanupScheduler}
 * (client event retention), {@code DeliveryReportHandler#retryBuffered}(먼저 온 발송 결과 재시도),
 * 목업 게이트웨이의 {@code MockDeliveryReportScheduler}(부트 기본 {@code TaskScheduler} 빈 사용),
 * {@code RetentionPurgeScheduler}(자동 파기).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
