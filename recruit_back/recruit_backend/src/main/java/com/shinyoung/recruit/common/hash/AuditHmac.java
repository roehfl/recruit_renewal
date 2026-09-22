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
 * 단, 가입자 중복 판정 키 {@link #identityHash} 는 예외 — 해당 메서드 설명 참고.
 */
public class AuditHmac {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String APPLICANT_PREFIX = "APPLICANT:";
    private static final String IDENTITY_PREFIX = "IDENTITY:";

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

    /**
     * 가입자 중복 판정 키. {@code HMAC_SHA256(secret, "IDENTITY:" + name + "|" + birthDate + "|" + gender)}.
     *
     * <p><b>클래스 규칙("원문 PII 를 넣지 않는다")의 명시적 예외다.</b> 그 규칙은 파기 후에도 남는 감사 연결자가
     * 개인정보로부터 계산되지 않게 하려는 것이다. 이 키는 {@code Applicant.ciHash} 에 저장되고 파기 때
     * {@code PURGED:} sentinel 로 덮어써져 사라지므로 파기 후 연결자가 되지 않는다. 접두로 감사 값과 도메인을 나눈다.
     *
     * <p>일반 SHA-256 을 쓰지 않는 이유: 이름이 같은 행에 평문으로 있어 생년월일·성별을 대입하면 역산된다.
     *
     * <p>각 값은 trim 해서 쓴다. 하나라도 null 이거나 blank 면 {@link IllegalArgumentException}.
     */
    public String identityHash(String name, String birthDate, String gender) {
        return hmacHex(IDENTITY_PREFIX
                + requireText(name, "name") + "|"
                + requireText(birthDate, "birthDate") + "|"
                + requireText(gender, "gender"));
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

    /** 값 자체는 메시지에 넣지 않는다(개인정보). */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identity " + field + " must not be blank.");
        }
        return value.trim();
    }
}
