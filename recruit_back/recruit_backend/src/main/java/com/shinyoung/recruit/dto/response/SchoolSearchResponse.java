package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.SchoolSource;

/**
 * 학교 자동완성 응답(경량). 지원자가 학교를 고를 때 필요한 식별·표시 필드만 노출한다(비민감).
 *
 * <p>{@code schoolCode}는 외부 OpenAPI가 주는 학교 식별자이며, 체계가 출처마다 다르므로
 * {@code schoolSource}와 함께 해석한다.
 */
public record SchoolSearchResponse(
        String schoolCode,
        String schoolName,
        SchoolSource schoolSource,
        String region
) {
}
