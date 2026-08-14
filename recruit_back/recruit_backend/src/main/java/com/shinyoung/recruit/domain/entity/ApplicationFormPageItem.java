package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "application_form_page_item",
        indexes = {
                @Index(name = "idx_application_form_page_item_page", columnList = "page_id"),
                @Index(name = "idx_application_form_page_item_sort", columnList = "page_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationFormPageItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private ApplicationFormPage page;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationSectionType sectionType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private ApplicationFormPageItem(
            ApplicationFormPage page,
            ApplicationSectionType sectionType,
            Integer sortOrder
    ) {
        validate(page, sectionType, sortOrder);
        this.page = page;
        this.sectionType = sectionType;
        this.sortOrder = sortOrder;
    }

    public static ApplicationFormPageItem create(
            ApplicationFormPage page,
            ApplicationSectionType sectionType,
            Integer sortOrder
    ) {
        return new ApplicationFormPageItem(page, sectionType, sortOrder);
    }

    public void assignPage(ApplicationFormPage page) {
        if (page == null) {
            throw new IllegalArgumentException("Application form page is required.");
        }
        this.page = page;
    }

    private static void validate(
            ApplicationFormPage page,
            ApplicationSectionType sectionType,
            Integer sortOrder
    ) {
        if (page == null) {
            throw new IllegalArgumentException("Application form page is required.");
        }
        if (sectionType == null) {
            throw new IllegalArgumentException("Application section type is required.");
        }
        if (!sectionType.isLayoutSection()) {
            throw new IllegalArgumentException("Unsupported layout section type: " + sectionType);
        }
        if (sortOrder == null || sortOrder < 0) {
            throw new IllegalArgumentException("Item sort order must be zero or positive.");
        }
    }
}
