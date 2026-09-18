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

    /**
     * 지원자 화면에 첨부 업로드 경로가 있는 섹션: 기본정보(증명사진), 경력(경력기술서).
     * 첨부 요구사항은 이 섹션에만 둘 수 있다. 다른 섹션의 요구사항은 필수일 때 제출을 영구히 막는다.
     */
    private static final Set<ApplicationSectionType> APPLICANT_ATTACHMENT_SECTION_TYPES = Collections.unmodifiableSet(EnumSet.of(
            BASIC_INFO,
            CAREER
    ));

    public boolean isLayoutSection() {
        return LAYOUT_SECTION_TYPES.contains(this);
    }

    public boolean acceptsApplicantAttachment() {
        return APPLICANT_ATTACHMENT_SECTION_TYPES.contains(this);
    }

    public static Set<ApplicationSectionType> layoutSectionTypes() {
        return LAYOUT_SECTION_TYPES;
    }
}
