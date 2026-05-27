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
    void config_null이고_질문_첨부_활성이면_BASIC_INFO와_외부_섹션만() {
        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(null, true, true);
        assertThat(enabled).containsExactlyInAnyOrder(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.QUESTION_ANSWER,
                ApplicationSectionType.ATTACHMENT
        );

        Set<ApplicationSectionType> required = ApplicationFormLayoutSectionPolicy.requiredSections(null, true, true);
        assertThat(required).containsExactlyInAnyOrder(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.QUESTION_ANSWER,
                ApplicationSectionType.ATTACHMENT
        );
    }

    @Test
    void 모든_config_false이고_외부_정책_없으면_BASIC_INFO만() {
        ApplicationFormConfig config = ApplicationFormConfig.create(
                false, false, false, false, false, false, false, false, false, false, false, false, false, false
        );

        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(config, false, false);
        assertThat(enabled).containsExactly(ApplicationSectionType.BASIC_INFO);

        Set<ApplicationSectionType> required = ApplicationFormLayoutSectionPolicy.requiredSections(config, false, false);
        assertThat(required).containsExactly(ApplicationSectionType.BASIC_INFO);
    }

    @Test
    void 질문_활성이지만_필수_아닌_경우_enabled에만_포함() {
        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(null, false, true);
        assertThat(enabled).contains(ApplicationSectionType.QUESTION_ANSWER);

        Set<ApplicationSectionType> required = ApplicationFormLayoutSectionPolicy.requiredSections(null, false, false);
        assertThat(required).doesNotContain(ApplicationSectionType.QUESTION_ANSWER);
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
