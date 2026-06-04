package com.shinyoung.recruit.common.hash;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 감사 연결용 HMAC-SHA256(Phase 09a). server pepper(AUDIT_HMAC_SECRET) 기반이며 {@link HashUtil}(plain SHA-256)
 * 과 다르다. {@code applicantRefHash} 처럼 "파기 후에도 같은 지원자를 가명으로 묶는" 감사 연결자에 쓴다.
 *
 * <p>입력에 {@code ci}/{@code ciHash}/{@code email}/{@code phone} 같은 원문 PII 를 넣지 않는다 — applicantId 등
 * 비-PII 식별자만 입력한다(ADR-0006, 리뷰 3차 #2). 단순 {@code SHA256(id)} 가 아니라 secret 기반 HMAC 이라
 * id 만으로 역산할 수 없다.
 */
public class AuditHmac {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String APPLICANT_PREFIX = "APPLICANT:";

    private final byte[] secret;

    public AuditHmac(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Audit HMAC secret must not be blank.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code HMAC_SHA256(secret, "APPLICANT:" + applicantId)}. applicantId 가 null 이면 null. */
    public String applicantRefHash(Long applicantId) {
        if (applicantId == null) {
            return null;
        }
        return hmacHex(APPLICANT_PREFIX + applicantId);
    }

    public String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute audit HMAC", e);
        }
    }
}
