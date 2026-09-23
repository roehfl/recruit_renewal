package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;

import java.time.LocalDateTime;

/**
 * 발송 이력 목록 1행. status·건수·delayed 는 조회할 때 수신자 채널 상태로 계산한 값이다(설계서 7.4).
 * title = 메일을 켰으면 메일 제목, 아니면 SMS 원문 앞 40자. 공고 없는 시스템 발송은 jobPostingTitle 이 null.
 */
public record MessageSendSummaryResponse(
        Long id,
        LocalDateTime requestedAt,
        MessageType type,
        boolean test,
        MessageOrigin origin,
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
        String senderName
) {

    private static final int SMS_TITLE_LENGTH = 40;

    public static MessageSendSummaryResponse of(MessageSend send, MessageChannelCountResponse mail,
                                                MessageChannelCountResponse sms, MessageSendStatus status,
                                                boolean delayed) {
        return new MessageSendSummaryResponse(
                send.getId(),
                send.getRequestedAt(),
                send.getType(),
                send.isTest(),
                send.getOrigin(),
                send.getJobPosting() == null ? null : send.getJobPosting().getTitle(),
                send.getStage() == null ? null : send.getStage().getStageName(),
                send.getConditionSummary(),
                titleOf(send),
                send.isMailEnabled(),
                send.isSmsEnabled(),
                send.getRecipientCount(),
                mail,
                sms,
                status,
                delayed,
                send.getSenderName()
        );
    }

    private static String titleOf(MessageSend send) {
        if (send.isMailEnabled()) {
            return send.getMailSubject();
        }
        String smsBody = send.getSmsBody();
        if (smsBody == null || smsBody.codePointCount(0, smsBody.length()) <= SMS_TITLE_LENGTH) {
            return smsBody;
        }
        return smsBody.substring(0, smsBody.offsetByCodePoints(0, SMS_TITLE_LENGTH));
    }
}
