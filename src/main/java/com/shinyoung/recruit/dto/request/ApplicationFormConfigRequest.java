package com.shinyoung.recruit.dto.request;

public record ApplicationFormConfigRequest(
        boolean useEducation,
        boolean useCareer,
        boolean useCertificate,
        boolean useLanguage,
        boolean useMilitary,
        boolean useAward,
        boolean useGapPeriod
) {
}
