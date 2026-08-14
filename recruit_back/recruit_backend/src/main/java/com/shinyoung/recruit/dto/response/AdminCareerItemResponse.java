package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.enumeration.EmploymentType;

import java.time.LocalDate;

public record AdminCareerItemResponse(
        Long careerId,
        String companyName,
        String departmentName,
        String positionTitle,
        EmploymentType employmentType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate promotionDate,
        Boolean currentlyEmployed,
        Integer currentSalary,
        String resignationReason,
        Integer sortOrder
) {

    public static AdminCareerItemResponse from(ApplicationCareer career) {
        return new AdminCareerItemResponse(
                career.getId(),
                career.getCompanyName(),
                career.getDepartmentName(),
                career.getPositionTitle(),
                career.getEmploymentType(),
                career.getStartDate(),
                career.getEndDate(),
                career.getPromotionDate(),
                career.getCurrentlyEmployed(),
                career.getCurrentSalary(),
                career.getResignationReason(),
                career.getSortOrder()
        );
    }
}
