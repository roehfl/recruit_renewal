// src/main/java/com/shinyoung/recruit/service/ClientEventRateLimiter.java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.exception.ClientEventRateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * client event 수집 in-memory 고정 윈도우(1분) rate limit(Phase 09f, 설계 6.4).
 *
 * <p>3단 — 1차 {@code ip} 글로벌(클라이언트가 sessionId를 바꿔도 우회 불가, 리뷰 Major 5),
 * 2차 {@code ip + clientSessionId}, 3차 인증 시 {@code principalHash}. 어느 단이든 초과하면
 * {@link ClientEventRateLimitExceededException}(429).
 *
 * <p>맵 크기 가드 — 만료 엔트리는 접근 시 lazy eviction하고, 정리 후에도 상한을 넘으면 신규 key를
 * 거부한다(map 폭증 방어, fail-closed). 운영 트래픽 증가 시 Redis/token bucket 전환은 별도 phase.
 */
@Component
public class ClientEventRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private record Window(long windowStart, AtomicInteger count) {
    }

    private final Clock clock;
    private final int perMinuteIp;
    private final int perMinuteSession;
    private final int perMinutePrincipal;
    private final int maxEntries;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ClientEventRateLimiter(
            Clock clock,
            @Value("${client-event-log.rate-limit.per-minute-ip:300}") int perMinuteIp,
            @Value("${client-event-log.rate-limit.per-minute-session:60}") int perMinuteSession,
            @Value("${client-event-log.rate-limit.per-minute-principal:120}") int perMinutePrincipal
    ) {
        this(clock, perMinuteIp, perMinuteSession, perMinutePrincipal, DEFAULT_MAX_ENTRIES);
    }

    ClientEventRateLimiter(Clock clock, int perMinuteIp, int perMinuteSession, int perMinutePrincipal, int maxEntries) {
        this.clock = clock;
        this.perMinuteIp = perMinuteIp;
        this.perMinuteSession = perMinuteSession;
        this.perMinutePrincipal = perMinutePrincipal;
        this.maxEntries = maxEntries;
    }

    /** 3단 한도 검사. principalHash는 미인증이면 null. 초과 시 429 예외. */
    public void check(String ip, String clientSessionId, String principalHash) {
        long now = clock.millis();
        increment("ip:" + ip, perMinuteIp, now);
        increment("session:" + ip + ":" + clientSessionId, perMinuteSession, now);
        if (principalHash != null) {
            increment("principal:" + principalHash, perMinutePrincipal, now);
        }
    }

    private void increment(String key, int limit, long now) {
        Window window = windows.get(key);
        if (window != null && expired(window, now)) {
            windows.remove(key, window);
            window = null;
        }
        if (window == null) {
            guardCapacity(key, now);
            window = windows.computeIfAbsent(key, k -> new Window(now, new AtomicInteger()));
        }
        if (window.count().incrementAndGet() > limit) {
            throw new ClientEventRateLimitExceededException("client event 수집 요청이 너무 많습니다.");
        }
    }

    private boolean expired(Window window, long now) {
        return now - window.windowStart() >= WINDOW_MILLIS;
    }

    /** 상한 도달 시 만료 엔트리 일괄 정리 후, 그래도 가득이면 신규 key 거부(fail-closed). */
    private void guardCapacity(String newKey, long now) {
        if (windows.size() < maxEntries || windows.containsKey(newKey)) {
            return;
        }
        windows.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        if (windows.size() >= maxEntries) {
            throw new ClientEventRateLimitExceededException("client event 수집 요청이 너무 많습니다.");
        }
    }
}
