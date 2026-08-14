package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationFormPageItemTest {

    @Test
    void create_success_for_layout_section() {
        ApplicationFormPage page = page();

        ApplicationFormPageItem item = ApplicationFormPageItem.create(page, ApplicationSectionType.BASIC_INFO, 0);

        assertThat(item.getPage()).isSameAs(page);
        assertThat(item.getSectionType()).isEqualTo(ApplicationSectionType.BASIC_INFO);
        assertThat(item.getSortOrder()).isZero();
    }

    @Test
    void create_fails_when_required_values_are_invalid() {
        ApplicationFormPage page = page();

        assertThatThrownBy(() -> ApplicationFormPageItem.create(null, ApplicationSectionType.BASIC_INFO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPageItem.create(page, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPageItem.create(page, ApplicationSectionType.APPLICATION, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPageItem.create(page, ApplicationSectionType.ETC, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPageItem.create(page, ApplicationSectionType.BASIC_INFO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPageItem.create(page, ApplicationSectionType.BASIC_INFO, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ApplicationFormPage page() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 9, 0);
        JobPosting jobPosting = JobPosting.create("Posting", "Content", now, now.plusDays(10));
        return ApplicationFormPage.create(jobPosting, 1, "Basic Info", null, 0);
    }
}
