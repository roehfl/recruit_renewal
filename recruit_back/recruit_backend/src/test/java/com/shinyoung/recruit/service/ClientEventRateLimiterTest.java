package com.shinyoung.recruit.service;

import com.shinyoung.recruit.exception.ClientEventRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 3단 고정 윈도우 rate limit(설계 6.4, 리뷰 Major 5) — clientSessionId는 client-controlled라
 * 단독 key로 쓰지 않고 ip 글로벌 한도가 1차다.
 */
class ClientEventRateLimiterTest {

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-10T12:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private ClientEventRateLimiter limiter(MutableClock clock, int ip, int session, int principal) {
        return new ClientEventRateLimiter(clock, ip, session, principal, 100);
    }

    @Test
    void 한도_내_요청은_허용된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 10, 5, 5);

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                limiter.check("10.0.0.1", "session-0001", null);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void session_한도_초과는_차단된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 100, 2, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.1", "session-0001", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0001", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void sessionId를_바꿔도_ip_글로벌_한도로_차단된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 3, 100, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.1", "session-0002", null);
        limiter.check("10.0.0.1", "session-0003", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0004", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void 인증_사용자는_principal_한도도_적용된다() {
        ClientEventRateLimiter limiter = limiter(new MutableClock(), 100, 100, 2);
        limiter.check("10.0.0.1", "session-0001", "hash-a");
        limiter.check("10.0.0.2", "session-0002", "hash-a"); // 다른 ip/session, 같은 principal

        assertThatThrownBy(() -> limiter.check("10.0.0.3", "session-0003", "hash-a"))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }

    @Test
    void 윈도우가_지나면_카운터가_회복된다() {
        MutableClock clock = new MutableClock();
        ClientEventRateLimiter limiter = limiter(clock, 100, 1, 100);
        limiter.check("10.0.0.1", "session-0001", null);
        assertThatThrownBy(() -> limiter.check("10.0.0.1", "session-0001", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.check("10.0.0.1", "session-0001", null))
                .doesNotThrowAnyException();
    }

    @Test
    void 맵_크기_상한을_넘으면_신규_key는_차단된다() {
        ClientEventRateLimiter limiter = new ClientEventRateLimiter(new MutableClock(), 1000, 1000, 1000, 4);
        // ip key + session key 2개씩 → 4 entry 채움
        limiter.check("10.0.0.1", "session-0001", null);
        limiter.check("10.0.0.2", "session-0002", null);

        assertThatThrownBy(() -> limiter.check("10.0.0.3", "session-0003", null))
                .isInstanceOf(ClientEventRateLimitExceededException.class);
    }
}
