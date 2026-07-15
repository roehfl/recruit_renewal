package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;

public record ApplicationFormConfigResponse(
        boolean useEducation,
        boolean requireEducation,
        boolean useCareer,
        boolean requireCareer,
        boolean useCertificate,
        boolean requireCertificate,
        boolean useLanguage,
        boolean requireLanguage,
        boolean useMilitary,
        boolean requireMilitary,
        boolean useAward,
        boolean requireAward,
        boolean useGapPeriod,
        boolean requireGapPeriod,
        boolean useAttachment
) {
    public static ApplicationFormConfigResponse from(ApplicationFormConfig config) {
        return new ApplicationFormConfigResponse(
                config.isUseEducation(),
                config.isRequireEducation(),
                config.isUseCareer(),
                config.isRequireCareer(),
                config.isUseCertificate(),
                config.isRequireCertificate(),
                config.isUseLanguage(),
                config.isRequireLanguage(),
                config.isUseMilitary(),
                config.isRequireMilitary(),
                config.isUseAward(),
                config.isRequireAward(),
                config.isUseGapPeriod(),
                config.isRequireGapPeriod(),
                config.isUseAttachment()
        );
    }
}
