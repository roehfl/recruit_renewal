package com.shinyoung.recruit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * School 수정 요청. {@code schoolCode}(식별 키)는 불변이라 포함하지 않는다. {@code active=false} 로 soft delete.
 */
public record SchoolUpdateRequest(
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
