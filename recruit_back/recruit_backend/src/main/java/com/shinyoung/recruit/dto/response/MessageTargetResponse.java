package com.shinyoung.recruit.dto.response;

import java.util.List;

/** 메시지 대상 조회 결과. interviewGroups 는 면접 2종에서만 채운다. */
public record MessageTargetResponse(
        List<MessageTargetRecipientResponse> recipients,
        List<String> interviewGroups,
        MessageSenderResponse sender
) {
}
