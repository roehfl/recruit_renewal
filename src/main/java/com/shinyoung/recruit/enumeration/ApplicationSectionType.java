package com.shinyoung.recruit.enumeration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ApplicationSectionType {
    BASIC_INFO,
    APPLICATION,
    EDUCATION,
    CAREER,
    CERTIFICATE,
    LANGUAGE,
    MILITARY,
    AWARD,
    GAP_PERIOD,
    QUESTION_ANSWER,
    ATTACHMENT,
    ETC;

    private static final Set<ApplicationSectionType> LAYOUT_SECTION_TYPES = Collections.unmodifiableSet(EnumSet.of(
            BASIC_INFO,
            MILITARY,
            EDUCATION,
            CAREER,
            CERTIFICATE,
            LANGUAGE,
            AWARD,
            GAP_PERIOD,
            QUESTION_ANSWER,
            ATTACHMENT
    ));

    public boolean isLayoutSection() {
        return LAYOUT_SECTION_TYPES.contains(this);
    }

    public static Set<ApplicationSectionType> layoutSectionTypes() {
        return LAYOUT_SECTION_TYPES;
    }
}
