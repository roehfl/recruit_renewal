package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationFormLayoutDefaultFactoryTest {

    private final ApplicationFormLayoutDefaultFactory factory = new ApplicationFormLayoutDefaultFactory();

    @Test
    void basic_info_only_creates_one_page() {
        List<ApplicationFormPage> pages = factory.createDefaultLayout(
                jobPosting(),
                Set.of(ApplicationSectionType.BASIC_INFO)
        );

        assertThat(pages).hasSize(1);
        assertThat(sectionTypes(pages.get(0))).containsExactly(ApplicationSectionType.BASIC_INFO);
    }

    @Test
    void creates_default_groups_without_empty_pages() {
        List<ApplicationFormPage> pages = factory.createDefaultLayout(
                jobPosting(),
                EnumSet.of(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.MILITARY,
                        ApplicationSectionType.EDUCATION,
                        ApplicationSectionType.CAREER,
                        ApplicationSectionType.CERTIFICATE,
                        ApplicationSectionType.LANGUAGE,
                        ApplicationSectionType.AWARD,
                        ApplicationSectionType.GAP_PERIOD,
                        ApplicationSectionType.QUESTION_ANSWER,
                        ApplicationSectionType.ATTACHMENT
                )
        );

        assertThat(pages).hasSize(5);
        assertThat(sectionTypes(pages.get(0))).containsExactly(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.MILITARY
        );
        assertThat(sectionTypes(pages.get(1))).containsExactly(
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER
        );
        assertThat(sectionTypes(pages.get(2))).containsExactly(
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.LANGUAGE,
                ApplicationSectionType.AWARD,
                ApplicationSectionType.GAP_PERIOD
        );
        assertThat(sectionTypes(pages.get(3))).containsExactly(ApplicationSectionType.QUESTION_ANSWER);
        assertThat(sectionTypes(pages.get(4))).containsExactly(ApplicationSectionType.ATTACHMENT);
        assertThat(pages).extracting(ApplicationFormPage::getPageNo).containsExactly(1, 2, 3, 4, 5);
        assertThat(pages).extracting(ApplicationFormPage::getSortOrder).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void skips_disabled_group_pages() {
        List<ApplicationFormPage> pages = factory.createDefaultLayout(
                jobPosting(),
                Set.of(
                        ApplicationSectionType.BASIC_INFO,
                        ApplicationSectionType.QUESTION_ANSWER,
                        ApplicationSectionType.ATTACHMENT
                )
        );

        assertThat(pages).hasSize(3);
        assertThat(sectionTypes(pages.get(0))).containsExactly(ApplicationSectionType.BASIC_INFO);
        assertThat(sectionTypes(pages.get(1))).containsExactly(ApplicationSectionType.QUESTION_ANSWER);
        assertThat(sectionTypes(pages.get(2))).containsExactly(ApplicationSectionType.ATTACHMENT);
    }

    private List<ApplicationSectionType> sectionTypes(ApplicationFormPage page) {
        return page.getItems().stream()
                .map(ApplicationFormPageItem::getSectionType)
                .toList();
    }

    private JobPosting jobPosting() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 9, 0);
        return JobPosting.create("Posting", "Content", now, now.plusDays(10));
    }
}
