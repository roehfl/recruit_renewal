package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationFormLayoutSectionPolicyTest {

    private static ApplicationFormConfig configWithUseAttachment(boolean useAttachment) {
        return ApplicationFormConfig.create(
                false, false, false, false, false, false, false, false, false, false, false, false, false, false,
                useAttachment
        );
    }

    @Test
    void useAttachment가_true면_요구사항_행_없이도_ATTACHMENT_노출() {
        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(
                configWithUseAttachment(true), false, false
        );

        assertThat(enabled).contains(ApplicationSectionType.ATTACHMENT);
    }

    @Test
    void useAttachment가_true여도_요구사항_행이_없으면_필수가_아니다() {
        Set<ApplicationSectionType> required = ApplicationFormLayoutSectionPolicy.requiredSections(
                configWithUseAttachment(true), false, false
        );

        assertThat(required).doesNotContain(ApplicationSectionType.ATTACHMENT);
    }

    @Test
    void useAttachment가_false여도_요구사항_행이_있으면_ATTACHMENT_노출() {
        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(
                configWithUseAttachment(false), true, false
        );

        assertThat(enabled).contains(ApplicationSectionType.ATTACHMENT);
    }

    @Test
    void 필수_요구사항이_있으면_useAttachment가_false여도_required는_enabled의_부분집합이다() {
        ApplicationFormConfig config = configWithUseAttachment(false);

        Set<ApplicationSectionType> enabled = ApplicationFormLayoutSectionPolicy.enabledSections(config, true, false);
        Set<ApplicationSectionType> required = ApplicationFormLayoutSectionPolicy.requiredSections(config, true, false);

        assertThat(required).contains(ApplicationSectionType.ATTACHMENT);
        assertThat(enabled).containsAll(required);
    }

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
                false,
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
                false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
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
                true,
                false
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
