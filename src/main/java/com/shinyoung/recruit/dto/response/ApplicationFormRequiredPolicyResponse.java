package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.enumeration.ApplicationFormRequirementType;

import java.util.List;

public record ApplicationFormRequiredPolicyResponse(
        List<ApplicationFormSectionPolicyResponse> sections,
        int requiredSectionCount,
        int optionalSectionCount,
        int requiredQuestionCount,
        int optionalQuestionCount,
        boolean hasRequiredQuestion,
        boolean attachmentRequired
) {

    public static ApplicationFormRequiredPolicyResponse from(
            ApplicationFormConfig config,
            JobPostingQuestionPolicyCount questionPolicyCount
    ) {
        return from(config, questionPolicyCount, null);
    }

    public static ApplicationFormRequiredPolicyResponse from(
            ApplicationFormConfig config,
            JobPostingQuestionPolicyCount questionPolicyCount,
            JobPostingAttachmentRequirementPolicyCount attachmentPolicyCount
    ) {
        if (config == null) {
            return disabledPolicy(attachmentPolicyCount);
        }
        return from(
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
                questionPolicyCount,
                attachmentPolicyCount
        );
    }

    public static ApplicationFormRequiredPolicyResponse from(
            Boolean useEducation,
            Boolean requireEducation,
            Boolean useCareer,
            Boolean requireCareer,
            Boolean useCertificate,
            Boolean requireCertificate,
            Boolean useLanguage,
            Boolean requireLanguage,
            Boolean useMilitary,
            Boolean requireMilitary,
            Boolean useAward,
            Boolean requireAward,
            Boolean useGapPeriod,
            Boolean requireGapPeriod,
            JobPostingQuestionPolicyCount questionPolicyCount
    ) {
        return from(
                useEducation,
                requireEducation,
                useCareer,
                requireCareer,
                useCertificate,
                requireCertificate,
                useLanguage,
                requireLanguage,
                useMilitary,
                requireMilitary,
                useAward,
                requireAward,
                useGapPeriod,
                requireGapPeriod,
                questionPolicyCount,
                null
        );
    }

    public static ApplicationFormRequiredPolicyResponse from(
            Boolean useEducation,
            Boolean requireEducation,
            Boolean useCareer,
            Boolean requireCareer,
            Boolean useCertificate,
            Boolean requireCertificate,
            Boolean useLanguage,
            Boolean requireLanguage,
            Boolean useMilitary,
            Boolean requireMilitary,
            Boolean useAward,
            Boolean requireAward,
            Boolean useGapPeriod,
            Boolean requireGapPeriod,
            JobPostingQuestionPolicyCount questionPolicyCount,
            JobPostingAttachmentRequirementPolicyCount attachmentPolicyCount
    ) {
        if (useEducation == null
                && requireEducation == null
                && useCareer == null
                && requireCareer == null
                && useCertificate == null
                && requireCertificate == null
                && useLanguage == null
                && requireLanguage == null
                && useMilitary == null
                && requireMilitary == null
                && useAward == null
                && requireAward == null
                && useGapPeriod == null
                && requireGapPeriod == null) {
            return disabledPolicy(attachmentPolicyCount);
        }

        JobPostingQuestionPolicyCount safeQuestionPolicyCount = questionPolicyCount == null
                ? JobPostingQuestionPolicyCount.empty(null)
                : questionPolicyCount;
        JobPostingAttachmentRequirementPolicyCount safeAttachmentPolicyCount = attachmentPolicyCount == null
                ? JobPostingAttachmentRequirementPolicyCount.empty(null)
                : attachmentPolicyCount;
        long activeQuestionCount = safeQuestionPolicyCount.activeQuestionCount();
        long requiredQuestionCount = safeQuestionPolicyCount.requiredQuestionCount();
        long optionalQuestionCount = Math.max(activeQuestionCount - requiredQuestionCount, 0);
        long totalAttachmentRequirementCount = safeAttachmentPolicyCount.totalRequirementCount();
        long requiredAttachmentRequirementCount = safeAttachmentPolicyCount.requiredRequirementCount();

        List<ApplicationFormSectionPolicyResponse> sections = List.of(
                formSection("EDUCATION", "Education", enabled(useEducation), required(useEducation, requireEducation)),
                formSection("CAREER", "Career", enabled(useCareer), required(useCareer, requireCareer)),
                formSection("CERTIFICATE", "Certificate", enabled(useCertificate), required(useCertificate, requireCertificate)),
                formSection("LANGUAGE", "Language", enabled(useLanguage), required(useLanguage, requireLanguage)),
                formSection("MILITARY", "Military", enabled(useMilitary), required(useMilitary, requireMilitary)),
                formSection("AWARD", "Award", enabled(useAward), required(useAward, requireAward)),
                formSection("GAP_PERIOD", "Gap Period", enabled(useGapPeriod), required(useGapPeriod, requireGapPeriod)),
                questionSection(activeQuestionCount, requiredQuestionCount),
                attachmentSection(totalAttachmentRequirementCount, requiredAttachmentRequirementCount)
        );

        return new ApplicationFormRequiredPolicyResponse(
                sections,
                countRequirementType(sections, ApplicationFormRequirementType.REQUIRED),
                countRequirementType(sections, ApplicationFormRequirementType.OPTIONAL),
                toIntCount(requiredQuestionCount),
                toIntCount(optionalQuestionCount),
                requiredQuestionCount > 0,
                requiredAttachmentRequirementCount > 0
        );
    }

    private static ApplicationFormRequiredPolicyResponse disabledPolicy() {
        return disabledPolicy(null);
    }

    private static ApplicationFormRequiredPolicyResponse disabledPolicy(
            JobPostingAttachmentRequirementPolicyCount attachmentPolicyCount
    ) {
        JobPostingAttachmentRequirementPolicyCount safeAttachmentPolicyCount = attachmentPolicyCount == null
                ? JobPostingAttachmentRequirementPolicyCount.empty(null)
                : attachmentPolicyCount;
        List<ApplicationFormSectionPolicyResponse> sections = List.of(
                ApplicationFormSectionPolicyResponse.disabled("EDUCATION", "Education", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("CAREER", "Career", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("CERTIFICATE", "Certificate", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("LANGUAGE", "Language", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("MILITARY", "Military", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("AWARD", "Award", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("GAP_PERIOD", "Gap Period", "Application form config is not available."),
                ApplicationFormSectionPolicyResponse.disabled("QUESTION", "Questions", "Application form config is not available."),
                attachmentSection(
                        safeAttachmentPolicyCount.totalRequirementCount(),
                        safeAttachmentPolicyCount.requiredRequirementCount()
                )
        );
        return new ApplicationFormRequiredPolicyResponse(
                sections,
                countRequirementType(sections, ApplicationFormRequirementType.REQUIRED),
                countRequirementType(sections, ApplicationFormRequirementType.OPTIONAL),
                0,
                0,
                false,
                safeAttachmentPolicyCount.requiredRequirementCount() > 0
        );
    }

    private static ApplicationFormSectionPolicyResponse formSection(
            String sectionCode,
            String displayName,
            boolean enabled,
            boolean required
    ) {
        if (!enabled) {
            return ApplicationFormSectionPolicyResponse.disabled(sectionCode, displayName, "This section is disabled.");
        }
        if (required) {
            return ApplicationFormSectionPolicyResponse.required(sectionCode, displayName, "This section is required.");
        }
        return ApplicationFormSectionPolicyResponse.optional(sectionCode, displayName, "This section is optional.");
    }

    private static ApplicationFormSectionPolicyResponse questionSection(long activeQuestionCount, long requiredQuestionCount) {
        if (activeQuestionCount <= 0) {
            return ApplicationFormSectionPolicyResponse.disabled("QUESTION", "Questions", "No active questions are configured.");
        }
        if (requiredQuestionCount > 0) {
            return ApplicationFormSectionPolicyResponse.required("QUESTION", "Questions", "At least one active question is required.");
        }
        return ApplicationFormSectionPolicyResponse.optional("QUESTION", "Questions", "Active questions are optional.");
    }

    private static ApplicationFormSectionPolicyResponse attachmentSection(
            long totalRequirementCount,
            long requiredRequirementCount
    ) {
        if (totalRequirementCount <= 0) {
            return ApplicationFormSectionPolicyResponse.disabled(
                    "ATTACHMENT",
                    "Attachment",
                    "No attachment requirements are configured."
            );
        }
        if (requiredRequirementCount > 0) {
            return ApplicationFormSectionPolicyResponse.required(
                    "ATTACHMENT",
                    "Attachment",
                    "Required attachment files are configured."
            );
        }
        return ApplicationFormSectionPolicyResponse.optional(
                "ATTACHMENT",
                "Attachment",
                "Attachment files are optional."
        );
    }

    private static boolean enabled(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static boolean required(Boolean enabled, Boolean required) {
        return enabled(enabled) && enabled(required);
    }

    private static int countRequirementType(
            List<ApplicationFormSectionPolicyResponse> sections,
            ApplicationFormRequirementType requirementType
    ) {
        return (int) sections.stream()
                .filter(section -> section.requirementType() == requirementType)
                .count();
    }

    private static int toIntCount(long count) {
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }
}
