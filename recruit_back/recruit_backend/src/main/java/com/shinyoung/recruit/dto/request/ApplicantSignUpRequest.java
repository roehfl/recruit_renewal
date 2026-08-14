package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Temporary signup API; production identity verification flow must replace client-provided ci.
 */
public record ApplicantSignUpRequest(
        @NotBlank(message = "loginId는 필수입니다.")
        @Size(max = 100, message = "loginId는 100자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 100, message = "password는 8자 이상 100자 이하여야 합니다.")
        String password,

        @NotBlank(message = "name은 필수입니다.")
        @Size(max = 100, message = "name은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "phoneNumber는 필수입니다.")
        @Size(max = 30, message = "phoneNumber는 30자 이하여야 합니다.")
        String phoneNumber,

        @Email(message = "유효한 이메일 형식이어야 합니다.")
        @Size(max = 255, message = "email은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "ci는 필수입니다.")
        @Size(max = 255, message = "ci는 255자 이하여야 합니다.")
        String ci
) {
}
