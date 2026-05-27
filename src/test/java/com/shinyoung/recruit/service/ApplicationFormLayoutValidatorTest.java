package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.exception.InvalidApplicationFormLayoutException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationFormLayoutValidatorTest {

    private final ApplicationFormLayoutValidator validator = new ApplicationFormLayoutValidator();

    @Test
    void valid_layout_passes() {
        Set<ApplicationSectionType> sections = EnumSet.of(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.MILITARY,
                ApplicationSectionType.QUESTION_ANSWER,
                ApplicationSectionType.ATTACHMENT
        );

        assertThatCode(() -> validator.validate(
                List.of(
                        page(1, 0, ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY),
                        page(2, 1, ApplicationSectionType.QUESTION_ANSWER),
                        page(3, 2, ApplicationSectionType.ATTACHMENT)
                ),
                sections,
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.QUESTION_ANSWER)
        )).doesNotThrowAnyException();
    }

    @Test
    void pages_null_or_empty_fails() {
        assertInvalid(() -> validator.validate(
                null,
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
        assertInvalid(() -> validator.validate(
                List.of(),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void duplicate_page_number_or_sort_order_fails() {
        assertInvalid(() -> validator.validate(
                List.of(
                        page(1, 0, ApplicationSectionType.BASIC_INFO),
                        page(1, 1, ApplicationSectionType.MILITARY)
                ),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));

        assertInvalid(() -> validator.validate(
                List.of(
                        page(1, 0, ApplicationSectionType.BASIC_INFO),
                        page(2, 0, ApplicationSectionType.MILITARY)
                ),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void page_without_items_fails() {
        ApplicationFormPage page = mock(ApplicationFormPage.class);
        when(page.getPageNo()).thenReturn(1);
        when(page.getSortOrder()).thenReturn(0);
        when(page.getTitle()).thenReturn("Basic Info");
        when(page.getItems()).thenReturn(List.of());

        assertInvalid(() -> validator.validate(
                List.of(page),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void duplicate_item_sort_order_or_section_type_fails() {
        ApplicationFormPage duplicateItemSortOrder = mock(ApplicationFormPage.class);
        List<ApplicationFormPageItem> items = List.of(
                item(ApplicationSectionType.BASIC_INFO, 0),
                item(ApplicationSectionType.MILITARY, 0)
        );
        when(duplicateItemSortOrder.getPageNo()).thenReturn(1);
        when(duplicateItemSortOrder.getSortOrder()).thenReturn(0);
        when(duplicateItemSortOrder.getTitle()).thenReturn("Basic Info");
        when(duplicateItemSortOrder.getItems()).thenReturn(items);

        assertInvalid(() -> validator.validate(
                List.of(duplicateItemSortOrder),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));

        assertInvalid(() -> validator.validate(
                List.of(
                        page(1, 0, ApplicationSectionType.BASIC_INFO),
                        page(2, 1, ApplicationSectionType.BASIC_INFO)
                ),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void unsupported_or_disabled_section_fails() {
        assertInvalid(() -> validator.validate(
                List.of(mockPageWithItem(ApplicationSectionType.APPLICATION)),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));

        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.MILITARY)),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void enabled_or_required_missing_fails() {
        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.BASIC_INFO)),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.ATTACHMENT),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));

        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.BASIC_INFO)),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.QUESTION_ANSWER),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.QUESTION_ANSWER)
        ));
    }

    @Test
    void basic_info_missing_from_enabled_sections_fails() {
        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.MILITARY)),
                Set.of(ApplicationSectionType.MILITARY),
                Set.of(ApplicationSectionType.BASIC_INFO)
        ));
    }

    @Test
    void basic_info_missing_from_required_sections_fails() {
        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.BASIC_INFO)),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of()
        ));
    }

    @Test
    void required_sections_must_be_enabled_subset() {
        assertInvalid(() -> validator.validate(
                List.of(page(1, 0, ApplicationSectionType.BASIC_INFO)),
                Set.of(ApplicationSectionType.BASIC_INFO),
                Set.of(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.QUESTION_ANSWER)
        ));
    }

    private void assertInvalid(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(InvalidApplicationFormLayoutException.class);
    }

    private ApplicationFormPage page(int pageNo, int sortOrder, ApplicationSectionType... sectionTypes) {
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting(), pageNo, "Page " + pageNo, null, sortOrder);
        for (int index = 0; index < sectionTypes.length; index++) {
            page.addItem(sectionTypes[index], index);
        }
        return page;
    }

    private ApplicationFormPage mockPageWithItem(ApplicationSectionType sectionType) {
        ApplicationFormPage page = mock(ApplicationFormPage.class);
        List<ApplicationFormPageItem> items = List.of(item(sectionType, 0));
        when(page.getPageNo()).thenReturn(1);
        when(page.getSortOrder()).thenReturn(0);
        when(page.getTitle()).thenReturn("Basic Info");
        when(page.getItems()).thenReturn(items);
        return page;
    }

    private ApplicationFormPageItem item(ApplicationSectionType sectionType, int sortOrder) {
        ApplicationFormPageItem item = mock(ApplicationFormPageItem.class);
        when(item.getSectionType()).thenReturn(sectionType);
        when(item.getSortOrder()).thenReturn(sortOrder);
        return item;
    }

    private JobPosting jobPosting() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 9, 0);
        return JobPosting.create("Posting", "Content", now, now.plusDays(10));
    }
}
