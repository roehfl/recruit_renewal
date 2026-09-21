package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.config.NiceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 만료된 본인확인 레코드 정리.
 *
 * <p>만료 판정 자체는 읽는 시점에도 하므로 이 정리는 보안 장치가 아니라
 * <b>메모리 누적 방지</b>가 목적이다. 늦게 돌아도 무방하다.
 *
 * <p>정리 기준은 요청 TTL 이 아니라 인증 완료 TTL 이다 — 요청 TTL 로 지우면
 * 아직 유효한 인증 완료 레코드를 없앨 수 있다.
 */
@Component
public class NiceVerificationCleanupScheduler {

    private final NiceVerificationStore store;
    private final NiceProperties properties;
    private final Clock clock;

    public NiceVerificationCleanupScheduler(
            NiceVerificationStore store, NiceProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${recruit.nice.cleanup-interval-ms:300000}")
    public void purgeExpired() {
        store.purgeExpired(
                clock.instant(), Duration.ofMinutes(properties.getVerifiedTtlMinutes()));
    }
}
