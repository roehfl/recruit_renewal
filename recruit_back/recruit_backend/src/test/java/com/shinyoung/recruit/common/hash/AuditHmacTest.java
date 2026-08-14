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
    void blank_secret이면_생성_실패() {
        assertThatThrownBy(() -> new AuditHmac("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditHmac(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
