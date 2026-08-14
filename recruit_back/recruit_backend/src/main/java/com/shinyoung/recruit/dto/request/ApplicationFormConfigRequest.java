package com.shinyoung.recruit.dto.request;

public record ApplicationFormConfigRequest(
        boolean useEducation,
        Boolean requireEducation,
        boolean useCareer,
        Boolean requireCareer,
        boolean useCertificate,
        Boolean requireCertificate,
        boolean useLanguage,
        Boolean requireLanguage,
        boolean useMilitary,
        Boolean requireMilitary,
        boolean useAward,
        Boolean requireAward,
        boolean useGapPeriod,
        Boolean requireGapPeriod,
        boolean useAttachment
) {
    public ApplicationFormConfigRequest(
            boolean useEducation,
            boolean useCareer,
            boolean useCertificate,
            boolean useLanguage,
            boolean useMilitary,
            boolean useAward,
            boolean useGapPeriod
    ) {
        this(
                useEducation,
                null,
                useCareer,
                null,
                useCertificate,
                null,
                useLanguage,
                null,
                useMilitary,
                null,
                useAward,
                null,
                useGapPeriod,
                null,
                false
        );
    }
}
