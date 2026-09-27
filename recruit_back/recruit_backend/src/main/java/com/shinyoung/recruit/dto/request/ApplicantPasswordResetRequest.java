package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.common.util.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

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

    /** 조합 규칙({@link PasswordPolicy}). 프론트와 같은 규칙이다. */
    @AssertTrue(message = PasswordPolicy.MESSAGE)
    public boolean isNewPasswordComplexEnough() {
        return PasswordPolicy.isAcceptable(newPassword);
    }

    /** BCrypt는 72바이트까지만 처리하고 넘으면 인코딩에서 예외(500)가 난다. 한글은 글자당 3바이트다. */
    @AssertTrue(message = "비밀번호는 72바이트 이하여야 합니다(한글은 약 24자).")
    public boolean isNewPasswordWithinBcryptLimit() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
