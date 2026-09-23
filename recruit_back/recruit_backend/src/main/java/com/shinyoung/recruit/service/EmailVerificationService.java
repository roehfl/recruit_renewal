package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import com.shinyoung.recruit.exception.InvalidEmailVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 이메일 인증번호(설계서 6.2): 숫자 6자리 · 유효 5분 · 같은 목적 재발송은 60초 뒤 · 5회 틀리면 무효 ·
 * 확인 후 10분 안에 가입/재설정. 세션은 컨트롤러가 읽고 쓰며 이 서비스는 값만 다룬다(NICE 패턴과 같다).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    static final int MAX_FAILURES = 5;

    private static final String SESSION_KEY_PREFIX = "EMAIL_VERIFICATION_";
    private static final String REISSUE_MESSAGE = "인증번호를 다시 받아 주세요.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SystemMailService systemMailService;
    private final Clock clock;

    /** 세션 속성 키. 목적별로 나눠 가입 인증과 비밀번호 재발급이 서로 덮어쓰지 않게 한다. */
    public static String sessionKey(EmailVerificationPurpose purpose) {
        return SESSION_KEY_PREFIX + purpose.name();
    }

    /**
     * 번호를 발급해 메일로 보내고 새 상태를 준다. 발송이 접수되지 않으면 예외 — 호출자는 세션에 저장하지 않는다.
     * 호출자는 트랜잭션 밖에서 부른다(발송 이력을 먼저 커밋해야 한다, SystemMailService).
     */
    public EmailVerificationState send(EmailVerificationState previous, EmailVerificationPurpose purpose,
                                       String email, String name) {
        IssuedCode issued = issue(previous, purpose, email);
        SystemMailOutcome outcome = systemMailService.send(purpose.getMessageType(), issued.state().getEmail(), name,
                Map.of(MessageVariable.VERIFICATION_CODE.getKey(), issued.code()), null, null);
        if (outcome == SystemMailOutcome.NO_TEMPLATE) {
            throw new InvalidEmailVerificationException("인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.");
        }
        if (outcome != SystemMailOutcome.ACCEPTED) {
            throw new InvalidEmailVerificationException("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return issued.state();
    }

    /** 새 번호와 상태. 같은 목적의 직전 발송에서 60초가 지나지 않았으면 거부한다. */
    public IssuedCode issue(EmailVerificationState previous, EmailVerificationPurpose purpose, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (previous != null && previous.getPurpose() == purpose
                && now.isBefore(previous.getSentAt().plus(RESEND_INTERVAL))) {
            throw new InvalidEmailVerificationException("인증번호는 60초 후에 다시 받을 수 있습니다.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationState state = EmailVerificationState.issued(
                purpose, email.trim(), HashUtil.sha256(code), now.plus(CODE_TTL), now);
        return new IssuedCode(state, code);
    }

    /** 번호를 확인한다. 틀리면 상태의 실패 수를 늘리고 예외, 맞으면 확인 시각을 남긴다. */
    public void verify(EmailVerificationState state, EmailVerificationPurpose purpose, String email, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (state == null || state.getPurpose() != purpose || !sameEmail(state.getEmail(), email)
                || !now.isBefore(state.getExpiresAt())) {
            throw new InvalidEmailVerificationException(REISSUE_MESSAGE);
        }
        if (state.getFailedCount() >= MAX_FAILURES) {
            throw new InvalidEmailVerificationException("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.");
        }
        if (code == null || !state.getCodeHash().equals(HashUtil.sha256(code.trim()))) {
            state.recordFailure();
            throw new InvalidEmailVerificationException("인증번호가 일치하지 않습니다.");
        }
        state.markVerified(now);
    }

    /** 확인했고 10분이 지나지 않았으며 같은 이메일(앞뒤 공백 제거, 대소문자 무시)이어야 통과한다. */
    public void requireVerified(EmailVerificationState state, EmailVerificationPurpose purpose, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean verified = state != null
                && state.getPurpose() == purpose
                && state.getVerifiedAt() != null
                && now.isBefore(state.getVerifiedAt().plus(VERIFIED_TTL))
                && sameEmail(state.getEmail(), email);
        if (!verified) {
            throw new InvalidEmailVerificationException("이메일 인증이 필요합니다.");
        }
    }

    private static boolean sameEmail(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }

    /** 발급 결과. code 는 메일로만 나가고 세션·DB·로그에 남지 않는다. */
    public record IssuedCode(EmailVerificationState state, String code) {
    }
}
