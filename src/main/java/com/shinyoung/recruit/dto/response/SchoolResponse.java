package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.School;

/**
 * admin 용 School 전체 응답.
 */
public record SchoolResponse(
        Long id,
        String schoolCode,
        String schoolName,
        String schoolType,
        String educationMode,
        String region,
        String address,
        String countryCode,
        boolean active
) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getSchoolCode(),
                school.getSchoolName(),
                school.getSchoolType(),
                school.getEducationMode(),
                school.getRegion(),
                school.getAddress(),
                school.getCountryCode(),
                school.isActive()
        );
    }
}
