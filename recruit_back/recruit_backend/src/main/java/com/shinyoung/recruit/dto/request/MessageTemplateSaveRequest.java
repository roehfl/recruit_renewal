package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 템플릿 등록·수정 공용. 채널 조합·변수 허용은 MessageTemplateService 가 검증한다. */
public record MessageTemplateSaveRequest(
        @NotNull MessageType type,
        @NotBlank @Size(max = 100) String name,
        @NotNull Boolean defaultTemplate,
        @Size(max = 200) String mailSubject,
        @Size(max = 10000) String mailBody,
        @Size(max = 2000) String smsBody
) {
}
