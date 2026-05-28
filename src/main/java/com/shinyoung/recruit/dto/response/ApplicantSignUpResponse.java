package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Applicant;

public record ApplicantSignUpResponse(
        Long applicantId,
        String loginId,
        String name
) {
    public static ApplicantSignUpResponse from(Applicant applicant) {
        return new ApplicantSignUpResponse(
                applicant.getId(),
                applicant.getLoginId(),
                applicant.getName()
        );
    }
}
