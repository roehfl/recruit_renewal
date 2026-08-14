package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;

public record ApplicationFormSectionResponse(
        ApplicationSectionType sectionType,
        String label,
        boolean enabled,
        boolean required,
        Integer sortOrder,
        Integer pageNo,
        String pageTitle
) {
    public static ApplicationFormSectionResponse of(
            ApplicationSectionType sectionType,
            boolean required,
            Integer sortOrder,
            Integer pageNo,
            String pageTitle
    ) {
        return new ApplicationFormSectionResponse(
                sectionType,
                labelOf(sectionType),
                true,
                required,
                sortOrder,
                pageNo,
                pageTitle
        );
    }

    private static String labelOf(ApplicationSectionType sectionType) {
        return switch (sectionType) {
            case BASIC_INFO -> "Basic Info";
            case EDUCATION -> "Education";
            case CAREER -> "Career";
            case MILITARY -> "Military";
            case CERTIFICATE -> "Certificate";
            case LANGUAGE -> "Language";
            case AWARD -> "Award";
            case GAP_PERIOD -> "Gap Period";
            case QUESTION_ANSWER -> "Questions";
            case ATTACHMENT -> "Attachment";
            case APPLICATION, ETC -> sectionType.name();
        };
    }
}
