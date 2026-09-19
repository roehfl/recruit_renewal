package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;

/** 발송별 (메일 상태, SMS 상태) 조합의 수신자 수. 이력 목록 건수 계산용 JPQL 생성자 projection. */
public record MessageRecipientStatusCount(
        Long messageSendId,
        MessageDeliveryStatus mailStatus,
        MessageDeliveryStatus smsStatus,
        Long count
) {
}
