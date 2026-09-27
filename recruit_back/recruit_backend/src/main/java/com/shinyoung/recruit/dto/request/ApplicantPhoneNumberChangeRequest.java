package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 전화번호는 통지 채널이므로 세션 탈취만으로 변조할 수 없도록 currentPassword 재확인을 요구한다.
 *
 * <p><b>새 번호는 요청 본문에 없다.</b> 세션의 NICE 본인확인 결과(용도 {@code PHONE_CHANGE})에서 꺼낸다.
 * 클라이언트가 보낸 번호를 믿으면 남의 번호로 안내 문자를 돌릴 수 있다.
 */
public record ApplicantPhoneNumberChangeRequest(
        @NotBlank(message = "currentPassword는 필수입니다.")
        String currentPassword
) {
}
