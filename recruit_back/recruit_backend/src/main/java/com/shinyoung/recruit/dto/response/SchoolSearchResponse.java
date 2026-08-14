package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.School;

/**
 * public 자동완성 응답(경량). 지원자가 학교를 고를 때 필요한 식별·표시 필드만 노출한다(비민감).
 */
public record SchoolSearchResponse(
        Long id,
        String schoolName,
        String schoolType,
        String region
) {

    public static SchoolSearchResponse from(School school) {
        return new SchoolSearchResponse(
                school.getId(),
                school.getSchoolName(),
                school.getSchoolType(),
                school.getRegion()
        );
    }
}
