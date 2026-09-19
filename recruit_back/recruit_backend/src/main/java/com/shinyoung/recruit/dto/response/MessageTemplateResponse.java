package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageTemplate;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;

public record MessageTemplateResponse(
        Long id,
        MessageType type,
        String name,
        boolean defaultTemplate,
        String mailSubject,
        String mailBody,
        String smsBody,
        LocalDateTime updatedAt
) {
    public static MessageTemplateResponse from(MessageTemplate template) {
        return new MessageTemplateResponse(
                template.getId(),
                template.getType(),
                template.getName(),
                template.isDefaultTemplate(),
                template.getMailSubject(),
                template.getMailBody(),
                template.getSmsBody(),
                template.getUpdatedAt()
        );
    }
}
