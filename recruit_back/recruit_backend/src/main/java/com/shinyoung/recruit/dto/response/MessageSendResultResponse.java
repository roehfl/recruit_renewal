package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageSendStatus;

/** 실제 발송 접수 결과. 발송은 비동기로 진행되며 excludedCount 는 요청했지만 조건에서 빠진 인원. */
public record MessageSendResultResponse(
        Long sendId,
        MessageSendStatus status,
        int recipientCount,
        int excludedCount
) {
}
