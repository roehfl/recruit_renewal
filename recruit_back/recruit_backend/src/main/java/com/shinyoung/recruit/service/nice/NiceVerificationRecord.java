package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationStatus;

import java.time.Instant;

/**
 * 본인확인 요청 1건. {@link NiceVerificationStore} 가 메모리에 들고 있는다.
 *
 * <p>불변이다. 상태가 바뀔 때마다 새 인스턴스를 만들어 교체한다 — 동시 접근에서
 * 부분 갱신된 상태가 보이지 않게 한다.
 *
 * <p>{@code sessionId} 는 요청을 낸 브라우저 세션이다. NICE 콜백에는 세션 쿠키가
 * 실리지 않으므로(cross-site POST) 콜백 시점에는 대조하지 못하고,
 * 뒤따르는 same-site 결과 교환에서 대조한다.
 */
public record NiceVerificationRecord(
        String reqSeq,
        NiceVerificationPurpose purpose,
        String sessionId,
        Instant issuedAt,
        NiceVerificationStatus status,
        String resultToken,
        Instant resultTokenIssuedAt,
        String name,
        String phoneNumber,
        String ci
) {

    public static NiceVerificationRecord pending(
            String reqSeq, NiceVerificationPurpose purpose, String sessionId, Instant issuedAt) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.PENDING, null, null, null, null, null);
    }

    public NiceVerificationRecord verified(
            String resultToken, Instant tokenIssuedAt, String name, String phoneNumber, String ci) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.VERIFIED, resultToken, tokenIssuedAt, name, phoneNumber, ci);
    }

    public NiceVerificationRecord failed(String resultToken, Instant tokenIssuedAt) {
        return new NiceVerificationRecord(
                reqSeq, purpose, sessionId, issuedAt,
                NiceVerificationStatus.FAIL, resultToken, tokenIssuedAt, null, null, null);
    }
}
