package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 지원자 가입 요청.
 *
 * <p><b>이름·휴대폰·CI 는 요청 본문에 없다.</b> NICE 본인확인 결과를 서버 세션에서 꺼내 쓴다.
 * 클라이언트가 보낸 값을 믿으면 본인확인이 무의미해지고 CI 중복 차단도 뚫린다.
 */
public record ApplicantSignUpRequest(
        @NotBlank(message = "loginId는 필수입니다.")
        @Size(max = 100, message = "loginId는 100자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 100, message = "password는 8자 이상 100자 이하여야 합니다.")
        String password,

        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email
) {
}
