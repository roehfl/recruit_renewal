package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;
import java.util.List;

/** 발송 이력 상세 = 목록 필드(같은 이름) + 치환 전 원문 + 수신자별 결과(id 순). */
public record MessageSendDetailResponse(
        Long id,
        LocalDateTime requestedAt,
        MessageType type,
        boolean test,
        String jobPostingTitle,
        String stageName,
        String conditionSummary,
        String title,
        boolean mailEnabled,
        boolean smsEnabled,
        int recipientCount,
        MessageChannelCountResponse mail,
        MessageChannelCountResponse sms,
        MessageSendStatus status,
        boolean delayed,
        String senderName,
        String templateName,
        String mailSubject,
        String mailBody,
        String smsBody,
        List<MessageRecipientResponse> recipients
) {

    public static MessageSendDetailResponse of(MessageSendSummaryResponse summary, MessageSend send,
                                               List<MessageRecipientResponse> recipients) {
        return new MessageSendDetailResponse(
                summary.id(),
                summary.requestedAt(),
                summary.type(),
                summary.test(),
                summary.jobPostingTitle(),
                summary.stageName(),
                summary.conditionSummary(),
                summary.title(),
                summary.mailEnabled(),
                summary.smsEnabled(),
                summary.recipientCount(),
                summary.mail(),
                summary.sms(),
                summary.status(),
                summary.delayed(),
                summary.senderName(),
                send.getTemplateName(),
                send.getMailSubject(),
                send.getMailBody(),
                send.getSmsBody(),
                recipients
        );
    }
}
