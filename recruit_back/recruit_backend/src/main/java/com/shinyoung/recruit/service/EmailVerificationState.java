package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HTTP 세션에 두는 이메일 인증 진행 상태(설계서 6.2). 번호 원문은 없고 SHA-256 해시만 있다.
 * 실패 수·확인 시각은 제자리에서 바뀐다 — 컨트롤러는 확인 뒤 같은 객체를 세션에 다시 넣는다.
 */
@Getter
public class EmailVerificationState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final EmailVerificationPurpose purpose;
    private final String email;
    private final String codeHash;
    private final LocalDateTime expiresAt;
    private final LocalDateTime sentAt;
    private int failedCount;
    private LocalDateTime verifiedAt;

    private EmailVerificationState(EmailVerificationPurpose purpose, String email, String codeHash,
                                   LocalDateTime expiresAt, LocalDateTime sentAt) {
        this.purpose = purpose;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.sentAt = sentAt;
    }

    public static EmailVerificationState issued(EmailVerificationPurpose purpose, String email, String codeHash,
                                                LocalDateTime expiresAt, LocalDateTime sentAt) {
        return new EmailVerificationState(purpose, email, codeHash, expiresAt, sentAt);
    }

    public void recordFailure() {
        this.failedCount++;
    }

    public void markVerified(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
