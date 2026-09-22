package com.shinyoung.recruit.common.hash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditHmacTest {

    private static final String SECRET = "test-audit-hmac-pepper-0001";
    private final AuditHmac auditHmac = new AuditHmac(SECRET);

    @Test
    void applicantRefHash는_결정적이고_plain_SHA256과_다르다() {
        String h1 = auditHmac.applicantRefHash(123L);
        String h2 = auditHmac.applicantRefHash(123L);

        assertThat(h1).isNotBlank();
        assertThat(h1).isEqualTo(h2);
        // ciHash 등에 쓰는 plain SHA-256 과 달라야 한다(역산 방지).
        assertThat(h1).isNotEqualTo(HashUtil.sha256("APPLICANT:123"));
        assertThat(h1).isNotEqualTo(HashUtil.sha256("123"));
    }

    @Test
    void secret이_다르면_같은_입력도_다른_hash() {
        AuditHmac other = new AuditHmac("different-secret-value");
        assertThat(auditHmac.applicantRefHash(123L)).isNotEqualTo(other.applicantRefHash(123L));
    }

    @Test
    void applicantId가_다르면_다른_hash() {
        assertThat(auditHmac.applicantRefHash(1L)).isNotEqualTo(auditHmac.applicantRefHash(2L));
    }

    @Test
    void applicantId가_null이면_null() {
        assertThat(auditHmac.applicantRefHash(null)).isNull();
    }

    @Test
    void identityHash는_같은_입력이면_같은_값() {
        assertThat(auditHmac.identityHash("홍길동", "19900101", "1"))
                .isNotBlank()
                .isEqualTo(auditHmac.identityHash("홍길동", "19900101", "1"));
    }

    @Test
    void identityHash는_이름이_다르면_다른_값() {
        assertThat(auditHmac.identityHash("홍길동", "19900101", "1"))
                .isNotEqualTo(auditHmac.identityHash("김철수", "19900101", "1"));
    }

    @Test
    void identityHash는_생년월일이_다르면_다른_값() {
        assertThat(auditHmac.identityHash("홍길동", "19900101", "1"))
                .isNotEqualTo(auditHmac.identityHash("홍길동", "19900102", "1"));
    }

    @Test
    void identityHash는_성별이_다르면_다른_값() {
        assertThat(auditHmac.identityHash("홍길동", "19900101", "1"))
                .isNotEqualTo(auditHmac.identityHash("홍길동", "19900101", "0"));
    }

    @Test
    void identityHash는_앞뒤_공백을_무시한다() {
        assertThat(auditHmac.identityHash(" 홍길동 ", " 19900101 ", " 1 "))
                .isEqualTo(auditHmac.identityHash("홍길동", "19900101", "1"));
    }

    @Test
    void identityHash는_secret이_다르면_다른_값() {
        AuditHmac other = new AuditHmac("different-secret-value");
        assertThat(auditHmac.identityHash("홍길동", "19900101", "1"))
                .isNotEqualTo(other.identityHash("홍길동", "19900101", "1"));
    }

    @Test
    void identityHash는_IDENTITY_접두로_감사_값과_도메인이_분리된다() {
        String identity = auditHmac.identityHash("홍길동", "19900101", "1");

        assertThat(identity).isEqualTo(auditHmac.hmacHex("IDENTITY:홍길동|19900101|1"));
        assertThat(identity).isNotEqualTo(auditHmac.hmacHex("홍길동|19900101|1"));
        assertThat(identity).isNotEqualTo(auditHmac.applicantRefHash(1L));
        // 키 없는 SHA-256 과도 달라야 한다(생년월일·성별 대입 역산 방지).
        assertThat(identity).isNotEqualTo(HashUtil.sha256("IDENTITY:홍길동|19900101|1"));
    }

    @Test
    void identityHash는_null이나_blank_입력이면_예외() {
        assertThatThrownBy(() -> auditHmac.identityHash(null, "19900101", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditHmac.identityHash("홍길동", null, "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditHmac.identityHash("홍길동", "19900101", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditHmac.identityHash("  ", "19900101", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditHmac.identityHash("홍길동", "", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditHmac.identityHash("홍길동", "19900101", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_secret이면_생성_실패() {
        assertThatThrownBy(() -> new AuditHmac("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditHmac(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
