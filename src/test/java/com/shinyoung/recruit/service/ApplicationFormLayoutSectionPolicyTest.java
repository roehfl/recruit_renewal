package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationFormLayoutSectionPolicyTest {

    @Test
    void basic_info_is_always_enabled_and_required() {
        assertThat(ApplicationFormLayoutSectionPolicy.enabledSections(null, false, false))
                .containsExactly(ApplicationSectionType.BASIC_INFO);
        assertThat(ApplicationFormLayoutSectionPolicy.requiredSections(null, false, false))
                .containsExactly(ApplicationSectionType.BASIC_INFO);
    }

    @Test
    void enabled_sections_follow_application_form_config_and_external_policies() {
        ApplicationFormConfig config = ApplicationFormConfig.create(
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                false
        );

        Set<ApplicationSectionType> sections = ApplicationFormLayoutSectionPolicy.enabledSections(config, true, true);

        assertThat(sections).containsExactlyInAnyOrder(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.MILITARY,
                ApplicationSectionType.GAP_PERIOD,
                ApplicationSectionType.QUESTION_ANSWER,
                ApplicationSectionType.ATTACHMENT
        );
    }

    @Test
    void required_sections_follow_required_flags_and_external_policies() {
        ApplicationFormConfig config = ApplicationFormConfig.create(
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                false,
                true,
                true
        );

        Set<ApplicationSectionType> sections = ApplicationFormLayoutSectionPolicy.requiredSections(config, true, true);

        assertThat(sections).containsExactlyInAnyOrder(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.MILITARY,
                ApplicationSectionType.GAP_PERIOD,
                ApplicationSectionType.QUESTION_ANSWER,
                ApplicationSectionType.ATTACHMENT
        );
    }
}
