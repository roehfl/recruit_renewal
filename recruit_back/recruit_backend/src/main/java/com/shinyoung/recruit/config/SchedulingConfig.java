package com.shinyoung.recruit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화(Phase 09f-4 — 프로젝트 최초 도입). 현재 사용처는
 * {@code ClientEventLogCleanupScheduler}(client event retention) 하나다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
