package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.LoginSecondFactorException;
import com.shinyoung.recruit.exception.LoginSecondFactorException.Reason;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 지원자 로그인의 NICE 2차 인증 검사. 비밀번호 검증이 끝난 뒤에 부른다.
 *
 * <p>로그인 요청의 {@code details} 에 담긴 세션의 NICE 결과(용도 {@code LOGIN})가 있어야 하고,
 * 5분 안이어야 하며, 인증 명의(이름+생년월일+성별 해시)가 계정의 {@code ciHash} 와 같아야 한다.
 * 휴대폰 번호는 쓰지 않는다 — 가입 중복 판정·휴대폰 변경과 같은 기준이다.
 *
 * <p>설정이 꺼져 있으면 아무것도 검사하지 않는다. 로그에는 사유 코드만 남기고 이름·생년월일은 남기지 않는다.
 */
@Component
public class LoginSecondFactorVerifier {

    private static final Logger log = LoggerFactory.getLogger(LoginSecondFactorVerifier.class);

    /** NICE 인증 완료 → 로그인 제출 허용 시간. 팝업이 닫히면 바로 제출하므로 가입 폼(30분)보다 짧게 둔다. */
    static final Duration VALID_FOR = Duration.ofMinutes(5);

    private final NiceProperties properties;
    private final AuditHmac auditHmac;
    private final Clock clock;

    public LoginSecondFactorVerifier(NiceProperties properties, AuditHmac auditHmac, Clock clock) {
        this.properties = properties;
        this.auditHmac = auditHmac;
        this.clock = clock;
    }

    public void verify(Object details, Applicant applicant) {
        if (!properties.isLoginTwoFactorEnabled()) {
            return;
        }
        if (!(details instanceof NiceVerifiedIdentity identity) || identity.purpose() != NiceVerificationPurpose.LOGIN) {
            throw fail(Reason.NICE_REQUIRED);
        }
        if (identity.verifiedAt().isBefore(clock.instant().minus(VALID_FOR))) {
            throw fail(Reason.EXPIRED);
        }
        String identityKey = auditHmac.identityHash(identity.name(), identity.birthDate(), identity.gender());
        if (!identityKey.equals(applicant.getCiHash())) {
            throw fail(Reason.MISMATCH);
        }
    }

    private LoginSecondFactorException fail(Reason reason) {
        log.warn("로그인 2차 인증 실패. reason={}", reason);
        return new LoginSecondFactorException(reason);
    }
}
