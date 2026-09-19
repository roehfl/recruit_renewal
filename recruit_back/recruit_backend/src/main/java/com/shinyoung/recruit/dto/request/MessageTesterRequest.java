package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 테스트 발송 수신자(인사팀 담당자). 이메일·휴대폰 중 1개 이상. */
public record MessageTesterRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 200) String email,
        @Size(max = 30) String phone
) {
}
