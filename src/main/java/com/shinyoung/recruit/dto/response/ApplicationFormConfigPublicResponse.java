package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;

public record ApplicationFormConfigPublicResponse(
        boolean useEducation,
        boolean useCareer,
        boolean useCertificate,
        boolean useLanguage,
        boolean useMilitary,
        boolean useAward,
        boolean useGapPeriod
) {
    public static ApplicationFormConfigPublicResponse from(ApplicationFormConfig config) {
        return new ApplicationFormConfigPublicResponse(
                config.isUseEducation(),
                config.isUseCareer(),
                config.isUseCertificate(),
                config.isUseLanguage(),
                config.isUseMilitary(),
                config.isUseAward(),
                config.isUseGapPeriod()
        );
    }
}
