package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;

import java.time.LocalDate;

public record AdminMilitaryResponse(
        Long militaryId,
        MilitarySubjectType militarySubjectType,
        MilitaryServiceType serviceType,
        MilitaryBranch militaryBranch,
        MilitaryRank rank,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        String exemptionReasonMasked
) {

    public static AdminMilitaryResponse from(ApplicationMilitary military, String exemptionReasonMasked) {
        return new AdminMilitaryResponse(
                military.getId(),
                military.getMilitarySubjectType(),
                military.getServiceType(),
                military.getMilitaryBranch(),
                military.getRank(),
                military.getServiceStartDate(),
                military.getServiceEndDate(),
                exemptionReasonMasked
        );
    }
}
