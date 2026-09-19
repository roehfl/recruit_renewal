package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.config.MessageProperties;

/** 미리보기 표시용 발신 정보(설정값). */
public record MessageSenderResponse(
        String name,
        String email,
        String smsCallbackNumber
) {
    public static MessageSenderResponse from(MessageProperties properties) {
        return new MessageSenderResponse(
                properties.getSenderName(),
                properties.getSenderEmail(),
                properties.getSmsCallbackNumber()
        );
    }
}
