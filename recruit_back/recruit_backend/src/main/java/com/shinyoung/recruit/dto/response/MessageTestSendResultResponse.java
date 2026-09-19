package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

/** 테스트 수신자 1명·채널 1개의 결과. */
public record MessageTestSendResultResponse(
        String name,
        MessageChannel channel,
        MessageDeliveryStatus status,
        String failureReason
) {
}
