package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;

import java.util.EnumSet;
import java.util.Set;

public final class ApplicationFormLayoutSectionPolicy {

    private ApplicationFormLayoutSectionPolicy() {
    }

    public static Set<ApplicationSectionType> enabledSections(
            ApplicationFormConfig config,
            boolean hasAttachmentRequirements,
            boolean hasActiveQuestions
    ) {
        EnumSet<ApplicationSectionType> sections = EnumSet.of(ApplicationSectionType.BASIC_INFO);
        if (config != null) {
            addConfiguredEnabledSections(sections, config);
        }
        if (hasActiveQuestions) {
            sections.add(ApplicationSectionType.QUESTION_ANSWER);
        }
        if (hasAttachmentRequirements) {
            sections.add(ApplicationSectionType.ATTACHMENT);
        }
        return Set.copyOf(sections);
    }

    public static Set<ApplicationSectionType> requiredSections(
            ApplicationFormConfig config,
            boolean hasRequiredAttachmentRequirements,
            boolean hasRequiredQuestions
    ) {
        EnumSet<ApplicationSectionType> sections = EnumSet.of(ApplicationSectionType.BASIC_INFO);
        if (config != null) {
            addConfiguredRequiredSections(sections, config);
        }
        if (hasRequiredQuestions) {
            sections.add(ApplicationSectionType.QUESTION_ANSWER);
        }
        if (hasRequiredAttachmentRequirements) {
            sections.add(ApplicationSectionType.ATTACHMENT);
        }
        return Set.copyOf(sections);
    }

    private static void addConfiguredEnabledSections(
            EnumSet<ApplicationSectionType> sections,
            ApplicationFormConfig config
    ) {
        if (config.isUseEducation()) {
            sections.add(ApplicationSectionType.EDUCATION);
        }
        if (config.isUseCareer()) {
            sections.add(ApplicationSectionType.CAREER);
        }
        if (config.isUseCertificate()) {
            sections.add(ApplicationSectionType.CERTIFICATE);
        }
        if (config.isUseLanguage()) {
            sections.add(ApplicationSectionType.LANGUAGE);
        }
        if (config.isUseMilitary()) {
            sections.add(ApplicationSectionType.MILITARY);
        }
        if (config.isUseAward()) {
            sections.add(ApplicationSectionType.AWARD);
        }
        if (config.isUseGapPeriod()) {
            sections.add(ApplicationSectionType.GAP_PERIOD);
        }
    }

    private static void addConfiguredRequiredSections(
            EnumSet<ApplicationSectionType> sections,
            ApplicationFormConfig config
    ) {
        if (config.isUseEducation() && config.isRequireEducation()) {
            sections.add(ApplicationSectionType.EDUCATION);
        }
        if (config.isUseCareer() && config.isRequireCareer()) {
            sections.add(ApplicationSectionType.CAREER);
        }
        if (config.isUseCertificate() && config.isRequireCertificate()) {
            sections.add(ApplicationSectionType.CERTIFICATE);
        }
        if (config.isUseLanguage() && config.isRequireLanguage()) {
            sections.add(ApplicationSectionType.LANGUAGE);
        }
        if (config.isUseMilitary() && config.isRequireMilitary()) {
            sections.add(ApplicationSectionType.MILITARY);
        }
        if (config.isUseAward() && config.isRequireAward()) {
            sections.add(ApplicationSectionType.AWARD);
        }
        if (config.isUseGapPeriod() && config.isRequireGapPeriod()) {
            sections.add(ApplicationSectionType.GAP_PERIOD);
        }
    }
}
