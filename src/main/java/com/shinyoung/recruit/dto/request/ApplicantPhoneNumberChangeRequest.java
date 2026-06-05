package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 전화번호는 통지 채널이므로 세션 탈취만으로 변조할 수 없도록 currentPassword 재확인을 요구한다.
 */
public record ApplicantPhoneNumberChangeRequest(
        @NotBlank(message = "currentPassword는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "phoneNumber는 필수입니다.")
        @Size(max = 30, message = "phoneNumber는 30자 이하여야 합니다.")
        String phoneNumber
) {
}
