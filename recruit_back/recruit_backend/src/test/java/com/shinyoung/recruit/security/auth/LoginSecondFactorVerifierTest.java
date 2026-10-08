package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.config.NiceProperties;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.LoginSecondFactorException;
import com.shinyoung.recruit.exception.LoginSecondFactorException.Reason;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSecondFactorVerifierTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    private final AuditHmac auditHmac = new AuditHmac("test-audit-hmac-pepper-0001");
    private final NiceProperties properties = new NiceProperties();
    private LoginSecondFactorVerifier verifier;
    private Applicant applicant;

    @BeforeEach
    void setUp() {
        verifier = new LoginSecondFactorVerifier(properties, auditHmac, Clock.fixed(NOW, ZoneId.of("UTC")));
        applicant = new Applicant(auditHmac.identityHash("홍길동", "19900101", "1"));
    }

    private NiceVerifiedIdentity identity(NiceVerificationPurpose purpose, String name, Instant verifiedAt) {
        return new NiceVerifiedIdentity(purpose, name, "01000000000", "19900101", "1", verifiedAt);
    }

    private void assertRejected(Object details, Reason reason) {
        assertThatThrownBy(() -> verifier.verify(details, applicant))
                .isInstanceOfSatisfying(LoginSecondFactorException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    @Test
    void 설정이_켜져_있고_명의가_같으면_통과한다() {
        assertThatCode(() -> verifier.verify(identity(NiceVerificationPurpose.LOGIN, "홍길동", NOW), applicant))
                .doesNotThrowAnyException();
    }

    @Test
    void 인증_결과가_없으면_거부한다() {
        assertRejected(null, Reason.NICE_REQUIRED);
    }

    @Test
    void 다른_용도의_인증_결과는_거부한다() {
        assertRejected(identity(NiceVerificationPurpose.SIGNUP, "홍길동", NOW), Reason.NICE_REQUIRED);
    }

    @Test
    void 인증_후_5분이_지나면_거부한다() {
        assertRejected(identity(NiceVerificationPurpose.LOGIN, "홍길동", NOW.minusSeconds(5 * 60 + 1)), Reason.EXPIRED);
    }

    @Test
    void 인증_후_5분_이내면_통과한다() {
        assertThatCode(() -> verifier.verify(identity(NiceVerificationPurpose.LOGIN, "홍길동", NOW.minusSeconds(5 * 60)), applicant))
                .doesNotThrowAnyException();
    }

    @Test
    void 명의가_다르면_거부한다() {
        assertRejected(identity(NiceVerificationPurpose.LOGIN, "김철수", NOW), Reason.MISMATCH);
    }

    @Test
    void 설정이_꺼져_있으면_결과가_없어도_통과한다() {
        properties.setLoginTwoFactorEnabled(false);

        assertThatCode(() -> verifier.verify(null, applicant)).doesNotThrowAnyException();
    }
}
