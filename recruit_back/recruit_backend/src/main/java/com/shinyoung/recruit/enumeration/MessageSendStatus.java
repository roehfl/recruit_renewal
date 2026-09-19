package com.shinyoung.recruit.enumeration;

/**
 * 발송 1회의 상태. 저장하지 않고 조회할 때 수신자 채널 상태로 계산한다(설계서 7.4):
 * PENDING 이 있으면 SENDING, 없고 REQUESTED 가 있으면 RESULT_PENDING, 둘 다 없으면 COMPLETED.
 */
public enum MessageSendStatus {
    SENDING,
    RESULT_PENDING,
    COMPLETED;

    /** 메일·SMS 채널을 합친 PENDING 수·REQUESTED 수로 상태를 정한다. */
    public static MessageSendStatus of(long pendingCount, long requestedCount) {
        if (pendingCount > 0) {
            return SENDING;
        }
        return requestedCount > 0 ? RESULT_PENDING : COMPLETED;
    }
}
