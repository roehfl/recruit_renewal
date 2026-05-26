package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.enumeration.ApplicationFormRequirementType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationFormPolicyResponseTest {

    @Test
    void public_config_response_returns_all_false_when_config_is_null() {
        ApplicationFormConfigPublicResponse response = ApplicationFormConfigPublicResponse.from(null);

        assertThat(response.useEducation()).isFalse();
        assertThat(response.useCareer()).isFalse();
        assertThat(response.useCertificate()).isFalse();
        assertThat(response.useLanguage()).isFalse();
        assertThat(response.useMilitary()).isFalse();
        assertThat(response.useAward()).isFalse();
        assertThat(response.useGapPeriod()).isFalse();
        assertThat(response.requireEducation()).isFalse();
        assertThat(response.requireCareer()).isFalse();
        assertThat(response.requireCertificate()).isFalse();
        assertThat(response.requireLanguage()).isFalse();
        assertThat(response.requireMilitary()).isFalse();
        assertThat(response.requireAward()).isFalse();
        assertThat(response.requireGapPeriod()).isFalse();
    }

    @Test
    void required_policy_response_returns_disabled_policy_when_config_is_null() {
        ApplicationFormRequiredPolicyResponse response = ApplicationFormRequiredPolicyResponse.from(
                null,
                JobPostingQuestionPolicyCount.empty(1L)
        );

        assertThat(response.requiredSectionCount()).isZero();
        assertThat(response.optionalSectionCount()).isZero();
        assertThat(response.requiredQuestionCount()).isZero();
        assertThat(response.optionalQuestionCount()).isZero();
        assertThat(response.sections())
                .extracting(ApplicationFormSectionPolicyResponse::requirementType)
                .containsExactly(
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED,
                        ApplicationFormRequirementType.DISABLED
                );
    }

    @Test
    void required_policy_response_uses_explicit_section_require_flags() {
        ApplicationFormRequiredPolicyResponse response = ApplicationFormRequiredPolicyResponse.from(
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                true,
                false,
                true,
                false,
                true,
                true,
                JobPostingQuestionPolicyCount.empty(1L)
        );

        assertThat(response.requiredSectionCount()).isEqualTo(3);
        assertThat(response.optionalSectionCount()).isEqualTo(3);
        assertThat(response.sections())
                .extracting(ApplicationFormSectionPolicyResponse::sectionCode, ApplicationFormSectionPolicyResponse::requirementType)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("EDUCATION", ApplicationFormRequirementType.OPTIONAL),
                        org.assertj.core.groups.Tuple.tuple("CAREER", ApplicationFormRequirementType.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple("CERTIFICATE", ApplicationFormRequirementType.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple("MILITARY", ApplicationFormRequirementType.OPTIONAL),
                        org.assertj.core.groups.Tuple.tuple("GAP_PERIOD", ApplicationFormRequirementType.REQUIRED)
                );
    }

    @Test
    void required_policy_response_derives_attachment_policy() {
        ApplicationFormRequiredPolicyResponse required = ApplicationFormRequiredPolicyResponse.from(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                JobPostingQuestionPolicyCount.empty(1L),
                new JobPostingAttachmentRequirementPolicyCount(1L, 2, 1)
        );

        assertThat(required.attachmentRequired()).isTrue();
        assertThat(required.requiredSectionCount()).isEqualTo(2);
        assertThat(required.optionalSectionCount()).isZero();
        assertThat(required.sections())
                .extracting(ApplicationFormSectionPolicyResponse::sectionCode, ApplicationFormSectionPolicyResponse::requirementType)
                .contains(org.assertj.core.groups.Tuple.tuple("ATTACHMENT", ApplicationFormRequirementType.REQUIRED));

        ApplicationFormRequiredPolicyResponse optional = ApplicationFormRequiredPolicyResponse.from(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                JobPostingQuestionPolicyCount.empty(1L),
                new JobPostingAttachmentRequirementPolicyCount(1L, 1, 0)
        );

        assertThat(optional.attachmentRequired()).isFalse();
        assertThat(optional.sections())
                .extracting(ApplicationFormSectionPolicyResponse::sectionCode, ApplicationFormSectionPolicyResponse::requirementType)
                .contains(org.assertj.core.groups.Tuple.tuple("ATTACHMENT", ApplicationFormRequirementType.OPTIONAL));
    }
}
