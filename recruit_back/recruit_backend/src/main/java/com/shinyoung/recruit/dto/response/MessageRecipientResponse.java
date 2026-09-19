package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.SmsKind;

/**
 * 이력 상세의 수신자 1명. 연락처는 가리지 않는다(설계서 12절, 2026-09-19 사용자 결정).
 * 파기된 수신자는 name·email·phone 이 null, 테스트 수신자(담당자)는 applicationId 가 null 이다.
 */
public record MessageRecipientResponse(
        Long id,
        Long applicationId,
        String name,
        String email,
        String phone,
        MessageDeliveryStatus mailStatus,
        String mailFailureReason,
        MessageDeliveryStatus smsStatus,
        String smsFailureReason,
        SmsKind smsKind
) {

    public static MessageRecipientResponse from(MessageRecipient recipient) {
        return new MessageRecipientResponse(
                recipient.getId(),
                recipient.getJobApplication() == null ? null : recipient.getJobApplication().getId(),
                recipient.getRecipientName(),
                recipient.getEmail(),
                recipient.getPhone(),
                recipient.getMailStatus(),
                recipient.getMailFailureReason(),
                recipient.getSmsStatus(),
                recipient.getSmsFailureReason(),
                recipient.getSmsKind()
        );
    }
}
