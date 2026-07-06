package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;

import java.time.LocalDate;

public record MilitaryResponse(
        Long militaryId,
        MilitarySubjectType militarySubjectType,
        MilitaryServiceType serviceType,
        MilitaryBranch militaryBranch,
        MilitaryRank rank,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        String nonServiceReason
) {

    public static MilitaryResponse from(ApplicationMilitary military) {
        return new MilitaryResponse(
                military.getId(),
                military.getMilitarySubjectType(),
                military.getServiceType(),
                military.getMilitaryBranch(),
                military.getRank(),
                military.getServiceStartDate(),
                military.getServiceEndDate(),
                military.getNonServiceReason()
        );
    }
}
