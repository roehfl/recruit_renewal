package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * School 생성 요청. {@code schoolCode} 는 식별 키(있으면 unique)로 생성 시 확정되며 이후 불변이다.
 * {@code schoolType}/{@code educationMode} 는 코드 문자열(백엔드 validation 미결합), {@code active} 미지정 시 true.
 */
public record SchoolCreateRequest(
        @Size(max = 100, message = "schoolCode은(는) 100자 이하여야 합니다.")
        String schoolCode,

        @NotBlank(message = "schoolName은(는) 필수입니다.")
        @Size(max = 200, message = "schoolName은(는) 200자 이하여야 합니다.")
        String schoolName,

        @Size(max = 50, message = "schoolType은(는) 50자 이하여야 합니다.")
        String schoolType,

        @Size(max = 50, message = "educationMode은(는) 50자 이하여야 합니다.")
        String educationMode,

        @Size(max = 100, message = "region은(는) 100자 이하여야 합니다.")
        String region,

        @Size(max = 500, message = "address은(는) 500자 이하여야 합니다.")
        String address,

        @Size(max = 10, message = "countryCode은(는) 10자 이하여야 합니다.")
        String countryCode,

        Boolean active
) {
}
