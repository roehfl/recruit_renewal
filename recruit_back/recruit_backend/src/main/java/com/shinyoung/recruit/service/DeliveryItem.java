package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;

/**
 * 수신자 1명·채널 1개의 치환 완료 내용. to 는 정규화한 이메일 또는 숫자만 남긴 번호.
 * 메일은 subject·body(일반 텍스트), SMS 는 body·smsKind 를 쓴다.
 */
public record DeliveryItem(
        Long recipientId,
        MessageChannel channel,
        String to,
        String subject,
        String body,
        SmsKind smsKind
) {
}
