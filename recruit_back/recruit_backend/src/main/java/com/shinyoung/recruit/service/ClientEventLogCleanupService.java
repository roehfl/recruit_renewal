package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * client event retention 삭제(Phase 09f-4, 설계 9장). {@code receivedAt < now - retentionDays} 기준
 * bulk delete. 진단 로그는 감사 로그와 달리 장기 보존하지 않는다(기본 90일).
 * 스케줄러(매일)와 관리자 수동 트리거 양쪽에서 호출된다.
 */
@Service
public class ClientEventLogCleanupService {

    private final ClientEventLogRepository repository;
    private final Clock clock;
    private final int retentionDays;

    public ClientEventLogCleanupService(
            ClientEventLogRepository repository,
            Clock clock,
            @Value("${client-event-log.retention-days:90}") int retentionDays
    ) {
        if (retentionDays < 1 || retentionDays > 365) {
            throw new IllegalArgumentException("client-event-log.retention-days must be between 1 and 365.");
        }
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /** 보존기간 경과 로그 삭제. 삭제 건수 반환. */
    @Transactional
    public int cleanup() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        return repository.deleteByReceivedAtBefore(threshold);
    }
}
