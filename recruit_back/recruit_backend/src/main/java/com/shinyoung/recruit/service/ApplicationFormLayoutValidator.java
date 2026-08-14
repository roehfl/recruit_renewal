package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.exception.InvalidApplicationFormLayoutException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationFormLayoutValidator {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    public void validate(
            List<ApplicationFormPage> pages,
            Set<ApplicationSectionType> effectiveEnabledSections,
            Set<ApplicationSectionType> effectiveRequiredSections
    ) {
        validateEffectiveSections(effectiveEnabledSections, effectiveRequiredSections);
        if (pages == null || pages.isEmpty()) {
            throw invalid("Layout must contain at least one page.");
        }

        Set<Integer> pageNos = new HashSet<>();
        Set<Integer> pageSortOrders = new HashSet<>();
        Set<ApplicationSectionType> placedSections = new HashSet<>();

        for (ApplicationFormPage page : pages) {
            validatePage(page, pageNos, pageSortOrders, placedSections, effectiveEnabledSections);
        }

        for (ApplicationSectionType sectionType : effectiveEnabledSections) {
            if (!placedSections.contains(sectionType)) {
                throw invalid("Enabled section is missing from layout: " + sectionType);
            }
        }
        for (ApplicationSectionType sectionType : effectiveRequiredSections) {
            if (!placedSections.contains(sectionType)) {
                throw invalid("Required section is missing from layout: " + sectionType);
            }
        }
        if (!placedSections.equals(effectiveEnabledSections)) {
            throw invalid("Placed sections must match effective enabled sections.");
        }
    }

    private void validateEffectiveSections(
            Set<ApplicationSectionType> effectiveEnabledSections,
            Set<ApplicationSectionType> effectiveRequiredSections
    ) {
        if (effectiveEnabledSections == null) {
            throw invalid("Effective enabled sections are required.");
        }
        if (effectiveRequiredSections == null) {
            throw invalid("Effective required sections are required.");
        }
        for (ApplicationSectionType sectionType : effectiveEnabledSections) {
            validateLayoutSectionType(sectionType);
        }
        for (ApplicationSectionType sectionType : effectiveRequiredSections) {
            validateLayoutSectionType(sectionType);
        }
        if (!effectiveEnabledSections.contains(ApplicationSectionType.BASIC_INFO)) {
            throw invalid("BASIC_INFO must be included in effective enabled sections.");
        }
        if (!effectiveRequiredSections.contains(ApplicationSectionType.BASIC_INFO)) {
            throw invalid("BASIC_INFO must be included in effective required sections.");
        }
        if (!effectiveEnabledSections.containsAll(effectiveRequiredSections)) {
            throw invalid("Effective required sections must be a subset of effective enabled sections.");
        }
    }

    private void validatePage(
            ApplicationFormPage page,
            Set<Integer> pageNos,
            Set<Integer> pageSortOrders,
            Set<ApplicationSectionType> placedSections,
            Set<ApplicationSectionType> effectiveEnabledSections
    ) {
        if (page == null) {
            throw invalid("Layout page is required.");
        }
        validatePositive(page.getPageNo(), "Page number");
        if (!pageNos.add(page.getPageNo())) {
            throw invalid("Duplicate page number: " + page.getPageNo());
        }
        validateZeroOrPositive(page.getSortOrder(), "Page sort order");
        if (!pageSortOrders.add(page.getSortOrder())) {
            throw invalid("Duplicate page sort order: " + page.getSortOrder());
        }
        validateTitle(page.getTitle());
        validateDescription(page.getDescription());

        List<ApplicationFormPageItem> items = page.getItems();
        if (items == null || items.isEmpty()) {
            throw invalid("Layout page must contain at least one item.");
        }

        Set<Integer> itemSortOrders = new HashSet<>();
        for (ApplicationFormPageItem item : items) {
            validateItem(item, itemSortOrders, placedSections, effectiveEnabledSections);
        }
    }

    private void validateItem(
            ApplicationFormPageItem item,
            Set<Integer> itemSortOrders,
            Set<ApplicationSectionType> placedSections,
            Set<ApplicationSectionType> effectiveEnabledSections
    ) {
        if (item == null) {
            throw invalid("Layout item is required.");
        }
        ApplicationSectionType sectionType = item.getSectionType();
        validateLayoutSectionType(sectionType);
        validateZeroOrPositive(item.getSortOrder(), "Item sort order");
        if (!itemSortOrders.add(item.getSortOrder())) {
            throw invalid("Duplicate item sort order in page: " + item.getSortOrder());
        }
        if (!placedSections.add(sectionType)) {
            throw invalid("Duplicate layout section type: " + sectionType);
        }
        if (!effectiveEnabledSections.contains(sectionType)) {
            throw invalid("Disabled section cannot be placed in layout: " + sectionType);
        }
    }

    private void validateLayoutSectionType(ApplicationSectionType sectionType) {
        if (sectionType == null) {
            throw invalid("Application section type is required.");
        }
        if (!sectionType.isLayoutSection()) {
            throw invalid("Unsupported layout section type: " + sectionType);
        }
    }

    private void validatePositive(Integer value, String label) {
        if (value == null || value <= 0) {
            throw invalid(label + " must be positive.");
        }
    }

    private void validateZeroOrPositive(Integer value, String label) {
        if (value == null || value < 0) {
            throw invalid(label + " must be zero or positive.");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw invalid("Page title is required.");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw invalid("Page title must be 100 characters or less.");
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.trim().length() > DESCRIPTION_MAX_LENGTH) {
            throw invalid("Page description must be 500 characters or less.");
        }
    }

    private InvalidApplicationFormLayoutException invalid(String message) {
        return new InvalidApplicationFormLayoutException(message);
    }
}
