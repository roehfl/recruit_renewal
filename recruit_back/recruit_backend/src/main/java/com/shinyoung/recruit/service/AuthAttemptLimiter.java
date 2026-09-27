package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.AuthAttemptLimitProperties;
import com.shinyoung.recruit.exception.AuthAttemptLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 로그인 실패·이메일 인증번호 발송/오답을 서버 전역(in-memory 고정 윈도우)으로 센다.
 *
 * <p>인증번호의 5회 한도와 60초 재발송 제한은 세션 안에만 있어, 세션을 새로 만들면 무제한으로 대입할 수 있었다.
 * 여기서는 로그인 아이디·이메일을 키로 세션과 무관하게 센다. IP 기준은 두지 않는다 — 프록시 헤더 처리가 없어
 * 모든 요청의 IP가 프록시 주소로 보이므로, IP 한도는 곧 전체 한도가 된다.
 *
 * <p>맵 크기 가드는 {@link ClientEventRateLimiter}와 같다. 만료 엔트리를 정리해도 상한이면 새 키를 거부한다(fail-closed).
 * 단일 인스턴스 전제다. 다중 인스턴스로 가면 공유 저장소로 옮긴다.
 */
@Component
public class AuthAttemptLimiter {

    static final int DEFAULT_MAX_ENTRIES = 100_000;
    private static final long MINUTE_MILLIS = 60_000L;

    private static final String LOGIN_MESSAGE = "로그인 시도가 너무 많습니다. %d분 후 다시 시도해 주세요.";
    private static final String CODE_SEND_MESSAGE = "인증번호 요청이 너무 많습니다. %d분 후 다시 시도해 주세요.";
    private static final String CODE_FAILURE_MESSAGE = "인증번호를 너무 많이 틀렸습니다. %d분 후 다시 시도해 주세요.";

    private record Window(long expiresAt, AtomicInteger count) {
    }

    private final Clock clock;
    private final AuthAttemptLimitProperties properties;
    private final int maxEntries;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthAttemptLimiter(Clock clock, AuthAttemptLimitProperties properties) {
        this(clock, properties, DEFAULT_MAX_ENTRIES);
    }

    AuthAttemptLimiter(Clock clock, AuthAttemptLimitProperties properties, int maxEntries) {
        this.clock = clock;
        this.properties = properties;
        this.maxEntries = maxEntries;
    }

    /** 로그인 전에 부른다. 실패 한도에 이미 닿았으면 인증을 시도하지 않고 429. */
    public void checkLogin(String loginId) {
        if (count(loginKey(loginId)) >= properties.getLoginMaxFailures()) {
            throw new AuthAttemptLimitExceededException(LOGIN_MESSAGE.formatted(properties.getLoginLockMinutes()));
        }
    }

    public void recordLoginFailure(String loginId) {
        increment(loginKey(loginId), properties.getLoginLockMinutes(), LOGIN_MESSAGE);
    }

    public void resetLogin(String loginId) {
        windows.remove(loginKey(loginId));
    }

    /** 인증번호 발송 1회를 센다. 한도를 넘으면 429. */
    public void acquireCodeSend(String email) {
        int count = increment("code-send:" + normalize(email), properties.getCodeWindowMinutes(), CODE_SEND_MESSAGE);
        if (count > properties.getCodeMaxSends()) {
            throw new AuthAttemptLimitExceededException(CODE_SEND_MESSAGE.formatted(properties.getCodeWindowMinutes()));
        }
    }

    /** 인증번호 비교 전에 부른다. 오답 한도에 닿았으면 비교하지 않고 429. */
    public void checkCodeVerify(String email) {
        if (count(codeFailureKey(email)) >= properties.getCodeMaxFailures()) {
            throw new AuthAttemptLimitExceededException(CODE_FAILURE_MESSAGE.formatted(properties.getCodeWindowMinutes()));
        }
    }

    public void recordCodeFailure(String email) {
        increment(codeFailureKey(email), properties.getCodeWindowMinutes(), CODE_FAILURE_MESSAGE);
    }

    private static String loginKey(String loginId) {
        return "login:" + normalize(loginId);
    }

    private static String codeFailureKey(String email) {
        return "code-failure:" + normalize(email);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int count(String key) {
        Window window = activeWindow(key, clock.millis());
        return window == null ? 0 : window.count().get();
    }

    private int increment(String key, int windowMinutes, String messageFormat) {
        long now = clock.millis();
        Window window = activeWindow(key, now);
        if (window == null) {
            guardCapacity(key, now, messageFormat.formatted(windowMinutes));
            window = windows.computeIfAbsent(key,
                    k -> new Window(now + windowMinutes * MINUTE_MILLIS, new AtomicInteger()));
        }
        return window.count().incrementAndGet();
    }

    private Window activeWindow(String key, long now) {
        Window window = windows.get(key);
        if (window != null && now >= window.expiresAt()) {
            windows.remove(key, window);
            return null;
        }
        return window;
    }

    /** 상한 도달 시 만료 엔트리를 정리하고, 그래도 가득이면 새 키를 거부한다(fail-closed). */
    private void guardCapacity(String newKey, long now, String message) {
        if (windows.size() < maxEntries || windows.containsKey(newKey)) {
            return;
        }
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
        if (windows.size() >= maxEntries) {
            throw new AuthAttemptLimitExceededException(message);
        }
    }
}
