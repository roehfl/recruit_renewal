package com.shinyoung.recruit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * client event retention 스케줄러(Phase 09f-4). 매일 1회(기본 04:00, cron 외부 설정) cleanup을 실행한다.
 * 실패해도 예외를 전파하지 않는다 — 진단 로그 정리 실패가 스케줄링 스레드/다음 실행에 영향을 주지 않게 한다.
 */
@Component
public class ClientEventLogCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClientEventLogCleanupScheduler.class);

    private final ClientEventLogCleanupService cleanupService;

    public ClientEventLogCleanupScheduler(ClientEventLogCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${client-event-log.cleanup-cron:0 0 4 * * *}")
    public void runCleanup() {
        try {
            int deleted = cleanupService.cleanup();
            log.info("Client event log cleanup deleted {} rows", deleted);
        } catch (Exception e) {
            log.error("Client event log cleanup failed", e);
        }
    }
}
