package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 인증번호 발송(가입·비밀번호 재발급 공용). */
public record EmailVerificationSendRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email
) {
}
