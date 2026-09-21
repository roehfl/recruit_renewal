package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 콜백 리다이렉트로 받은 1회용 토큰을 인증 결과로 교환하는 요청. */
public record NiceResultRequest(
        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
