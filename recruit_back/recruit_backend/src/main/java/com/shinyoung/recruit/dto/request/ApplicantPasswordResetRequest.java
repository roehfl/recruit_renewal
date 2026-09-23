package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 전 비밀번호 재설정. 같은 세션에서 인증번호를 확인한 뒤 10분 안에 보낸다. 비밀번호 규칙은 가입과 같다. */
public record ApplicantPasswordResetRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "newPassword는 필수입니다.")
        @Size(min = 8, max = 100, message = "newPassword는 8자 이상 100자 이하여야 합니다.")
        String newPassword
) {
}
