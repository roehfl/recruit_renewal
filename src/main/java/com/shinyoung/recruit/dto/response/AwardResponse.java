package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationAward;

import java.time.LocalDate;

public record AwardResponse(
        Long awardId,
        String awardName,
        String awardingOrganization,
        LocalDate awardDate,
        String description,
        Integer sortOrder
) {

    public static AwardResponse from(ApplicationAward award) {
        return new AwardResponse(
                award.getId(),
                award.getAwardName(),
                award.getAwardingOrganization(),
                award.getAwardDate(),
                award.getDescription(),
                award.getSortOrder()
        );
    }
}
