package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationFormPageTest {

    @Test
    void create_success() {
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting(), 1, " Basic Info ", " Description ", 0);

        assertThat(page.getPageNo()).isEqualTo(1);
        assertThat(page.getTitle()).isEqualTo("Basic Info");
        assertThat(page.getDescription()).isEqualTo("Description");
        assertThat(page.getSortOrder()).isZero();
    }

    @Test
    void create_fails_when_required_values_are_invalid() {
        assertThatThrownBy(() -> ApplicationFormPage.create(null, 1, "Basic Info", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), null, "Basic Info", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 0, "Basic Info", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 1, " ", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 1, "a".repeat(101), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 1, "Basic Info", "a".repeat(501), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 1, "Basic Info", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationFormPage.create(jobPosting(), 1, "Basic Info", null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void add_item_connects_page_and_item() {
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting(), 1, "Basic Info", null, 0);

        ApplicationFormPageItem item = page.addItem(ApplicationSectionType.BASIC_INFO, 0);

        assertThat(page.hasItems()).isTrue();
        assertThat(page.getItems()).containsExactly(item);
        assertThat(item.getPage()).isSameAs(page);
    }

    @Test
    void clear_items_removes_items() {
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting(), 1, "Basic Info", null, 0);
        page.addItem(ApplicationSectionType.BASIC_INFO, 0);

        page.clearItems();

        assertThat(page.hasItems()).isFalse();
    }

    private JobPosting jobPosting() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 9, 0);
        return JobPosting.create("Posting", "Content", now, now.plusDays(10));
    }
}
