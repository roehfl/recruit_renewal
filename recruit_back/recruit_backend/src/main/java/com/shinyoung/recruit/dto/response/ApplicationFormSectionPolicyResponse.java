package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.ApplicationFormRequirementType;

public record ApplicationFormSectionPolicyResponse(
        String sectionCode,
        String displayName,
        boolean enabled,
        boolean required,
        ApplicationFormRequirementType requirementType,
        String description
) {

    static ApplicationFormSectionPolicyResponse required(String sectionCode, String displayName, String description) {
        return new ApplicationFormSectionPolicyResponse(
                sectionCode,
                displayName,
                true,
                true,
                ApplicationFormRequirementType.REQUIRED,
                description
        );
    }

    static ApplicationFormSectionPolicyResponse optional(String sectionCode, String displayName, String description) {
        return new ApplicationFormSectionPolicyResponse(
                sectionCode,
                displayName,
                true,
                false,
                ApplicationFormRequirementType.OPTIONAL,
                description
        );
    }

    static ApplicationFormSectionPolicyResponse disabled(String sectionCode, String displayName, String description) {
        return new ApplicationFormSectionPolicyResponse(
                sectionCode,
                displayName,
                false,
                false,
                ApplicationFormRequirementType.DISABLED,
                description
        );
    }

    static ApplicationFormSectionPolicyResponse deferred(String sectionCode, String displayName, String description) {
        return new ApplicationFormSectionPolicyResponse(
                sectionCode,
                displayName,
                false,
                false,
                ApplicationFormRequirementType.DEFERRED,
                description
        );
    }
}
