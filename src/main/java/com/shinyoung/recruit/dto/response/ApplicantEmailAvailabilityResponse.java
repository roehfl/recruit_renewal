package com.shinyoung.recruit.dto.response;

/**
 * 이메일 가용성 advisory 응답. 가입 여부 외 어떤 정보도 노출하지 않는다.
 */
public record ApplicantEmailAvailabilityResponse(
        boolean available
) {
}
