package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.enumeration.GapType;

import java.time.LocalDate;

public record AdminGapPeriodResponse(
        Long gapPeriodId,
        LocalDate startDate,
        LocalDate endDate,
        GapType gapType,
        String reason,
        String description,
        Integer sortOrder
) {

    public static AdminGapPeriodResponse from(ApplicationGapPeriod gapPeriod) {
        return new AdminGapPeriodResponse(
                gapPeriod.getId(),
                gapPeriod.getStartDate(),
                gapPeriod.getEndDate(),
                gapPeriod.getGapType(),
                gapPeriod.getReason(),
                gapPeriod.getDescription(),
                gapPeriod.getSortOrder()
        );
    }
}
