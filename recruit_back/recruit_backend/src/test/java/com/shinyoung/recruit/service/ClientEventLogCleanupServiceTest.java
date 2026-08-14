package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ClientEventLog;
import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** retention cleanup(설계 9장) — receivedAt 기준 경계 삭제. 기본 retention-days=90(테스트는 동일 기본값 가정). */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ClientEventLogCleanupServiceTest {

    @Autowired
    ClientEventLogCleanupService cleanupService;

    @Autowired
    ClientEventLogRepository repository;

    @Autowired
    Clock clock;

    private void seed(String sessionId, LocalDateTime receivedAt) {
        repository.save(ClientEventLog.builder()
                .receivedAt(receivedAt)
                .eventType(ClientEventType.API_ERROR)
                .severity(ClientEventSeverity.ERROR)
                .source(ClientEventSource.APPLICANT_WEB)
                .clientSessionId(sessionId)
                .clientEventId("event-" + sessionId)
                .build());
    }

    @Test
    void 보존기간이_지난_로그만_삭제된다() {
        LocalDateTime now = LocalDateTime.now(clock);
        seed("session-old-0001", now.minusDays(91));   // 보존기간(90일) 밖 — 삭제 대상
        seed("session-keep-001", now.minusDays(89));   // 보존기간 안 — 유지
        seed("session-keep-002", now.minusDays(1));    // 유지

        int deleted = cleanupService.cleanup();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void 삭제_대상이_없으면_0을_반환한다() {
        seed("session-keep-001", LocalDateTime.now(clock).minusDays(1));

        assertThat(cleanupService.cleanup()).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    // ── retention-days 범위 가드(단위 테스트 — Spring 컨텍스트 불필요) ──────────────

    @Test
    void retention_days가_0이면_생성시_예외가_발생한다() {
        assertThatThrownBy(() ->
                new ClientEventLogCleanupService(mock(ClientEventLogRepository.class), Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    void retention_days가_366이면_생성시_예외가_발생한다() {
        assertThatThrownBy(() ->
                new ClientEventLogCleanupService(mock(ClientEventLogRepository.class), Clock.systemUTC(), 366))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 and 365");
    }

    @Test
    void retention_days가_유효한_값이면_정상_생성된다() {
        assertThatCode(() ->
                new ClientEventLogCleanupService(mock(ClientEventLogRepository.class), Clock.systemUTC(), 90))
                .doesNotThrowAnyException();
    }
}
