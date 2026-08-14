package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * School 생성 요청. 식별/중복제거는 {@code (schoolName, schoolType, region)} 기준이다.
 * {@code schoolType}/{@code schoolCategory}/{@code educationMode} 는 코드 문자열(백엔드 validation 미결합),
 * {@code active} 미지정 시 true.
 */
public record SchoolCreateRequest(
        @NotBlank(message = "schoolName은(는) 필수입니다.")
        @Size(max = 200, message = "schoolName은(는) 200자 이하여야 합니다.")
        String schoolName,

        @Size(max = 50, message = "schoolType은(는) 50자 이하여야 합니다.")
        String schoolType,

        @Size(max = 50, message = "schoolCategory은(는) 50자 이하여야 합니다.")
        String schoolCategory,

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
