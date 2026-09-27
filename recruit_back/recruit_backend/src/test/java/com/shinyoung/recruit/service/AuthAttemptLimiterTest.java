package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AuthAttemptLimitProperties;
import com.shinyoung.recruit.exception.AuthAttemptLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAttemptLimiterTest {

    /** 시간을 앞으로 돌릴 수 있는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-27T01:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AuthAttemptLimiter limiter = new AuthAttemptLimiter(clock, new AuthAttemptLimitProperties());

    @Test
    void 로그인_잠금은_윈도우가_지나면_풀린다() {
        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure("emp01");
        }
        assertThatThrownBy(() -> limiter.checkLogin("emp01")).isInstanceOf(AuthAttemptLimitExceededException.class);

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkLogin("emp01")).doesNotThrowAnyException();
    }

    @Test
    void 인증번호_발송_한도는_윈도우가_지나면_다시_센다() {
        for (int i = 0; i < 5; i++) {
            limiter.acquireCodeSend("applicant@example.com");
        }
        assertThatThrownBy(() -> limiter.acquireCodeSend("applicant@example.com"))
                .isInstanceOf(AuthAttemptLimitExceededException.class);

        clock.advance(Duration.ofMinutes(60));

        assertThatCode(() -> limiter.acquireCodeSend("applicant@example.com")).doesNotThrowAnyException();
    }

    @Test
    void 맵이_가득_차면_만료_엔트리를_정리하고_그래도_가득이면_새_키를_거부한다() {
        AuthAttemptLimiter small = new AuthAttemptLimiter(clock, new AuthAttemptLimitProperties(), 2);
        small.recordLoginFailure("a");
        small.recordLoginFailure("b");

        assertThatThrownBy(() -> small.recordLoginFailure("c")).isInstanceOf(AuthAttemptLimitExceededException.class);
        assertThatCode(() -> small.recordLoginFailure("a")).as("이미 있는 키는 계속 센다").doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> small.recordLoginFailure("c")).doesNotThrowAnyException();
    }
}
