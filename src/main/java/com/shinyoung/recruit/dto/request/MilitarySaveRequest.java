package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MilitarySaveRequest(
        @NotNull MilitarySubjectType militarySubjectType,
        MilitaryServiceType serviceType,
        MilitaryBranch militaryBranch,
        MilitaryRank rank,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        @Size(max = 1000) String nonServiceReason
) {
}
