package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

import java.util.Map;

/** 채널 하나의 상태별 수신자 수. 끈 채널은 수신자가 모두 skipped 다. */
public record MessageChannelCountResponse(
        long pending,
        long requested,
        long sent,
        long failed,
        long skipped
) {

    public static MessageChannelCountResponse of(Map<MessageDeliveryStatus, Long> counts) {
        return new MessageChannelCountResponse(
                counts.getOrDefault(MessageDeliveryStatus.PENDING, 0L),
                counts.getOrDefault(MessageDeliveryStatus.REQUESTED, 0L),
                counts.getOrDefault(MessageDeliveryStatus.SENT, 0L),
                counts.getOrDefault(MessageDeliveryStatus.FAILED, 0L),
                counts.getOrDefault(MessageDeliveryStatus.SKIPPED, 0L)
        );
    }
}
