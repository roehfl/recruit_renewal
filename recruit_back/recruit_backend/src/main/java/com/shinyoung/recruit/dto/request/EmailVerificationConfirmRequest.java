package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 인증번호 확인(가입·비밀번호 재발급 공용). 번호 형식은 보지 않는다 — 틀린 값은 불일치로 센다. */
public record EmailVerificationConfirmRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Size(max = 20, message = "인증번호가 너무 깁니다.")
        String code
) {
}
