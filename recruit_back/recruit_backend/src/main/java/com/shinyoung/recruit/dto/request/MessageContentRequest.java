package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 발송 화면에서 작성한 내용(치환 전). 켠 채널의 필수 입력은 MessageSendService 가 검증한다. */
public record MessageContentRequest(
        Long templateId,
        @NotNull Boolean mailEnabled,
        @NotNull Boolean smsEnabled,
        @Size(max = 200) String mailSubject,
        @Size(max = 10000) String mailBody,
        @Size(max = 2000) String smsBody
) {
}
