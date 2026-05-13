package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;

public record ApplicationFormConfigResponse(
        boolean useEducation,
        boolean useCareer,
        boolean useCertificate,
        boolean useLanguage,
        boolean useMilitary,
        boolean useAward,
        boolean useGapPeriod
) {
    public static ApplicationFormConfigResponse from(ApplicationFormConfig config) {
        return new ApplicationFormConfigResponse(
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
