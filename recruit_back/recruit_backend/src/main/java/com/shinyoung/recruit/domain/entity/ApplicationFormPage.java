package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "application_form_page",
        indexes = {
                @Index(name = "idx_application_form_page_posting", columnList = "job_posting_id"),
                @Index(name = "idx_application_form_page_sort", columnList = "job_posting_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationFormPage extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private Integer pageNo;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ApplicationFormPageItem> items = new ArrayList<>();

    private ApplicationFormPage(
            JobPosting jobPosting,
            Integer pageNo,
            String title,
            String description,
            Integer sortOrder
    ) {
        validate(jobPosting, pageNo, title, description, sortOrder);
        this.jobPosting = jobPosting;
        this.pageNo = pageNo;
        this.title = title.trim();
        this.description = trimToNull(description);
        this.sortOrder = sortOrder;
    }

    public static ApplicationFormPage create(
            JobPosting jobPosting,
            Integer pageNo,
            String title,
            String description,
            Integer sortOrder
    ) {
        return new ApplicationFormPage(jobPosting, pageNo, title, description, sortOrder);
    }

    public ApplicationFormPageItem addItem(ApplicationSectionType sectionType, Integer sortOrder) {
        ApplicationFormPageItem item = ApplicationFormPageItem.create(this, sectionType, sortOrder);
        this.items.add(item);
        return item;
    }

    public void addItem(ApplicationFormPageItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Application form page item is required.");
        }
        item.assignPage(this);
        this.items.add(item);
    }

    public void clearItems() {
        this.items.clear();
    }

    public boolean hasItems() {
        return !this.items.isEmpty();
    }

    private static void validate(
            JobPosting jobPosting,
            Integer pageNo,
            String title,
            String description,
            Integer sortOrder
    ) {
        if (jobPosting == null) {
            throw new IllegalArgumentException("Job posting is required.");
        }
        if (pageNo == null || pageNo <= 0) {
            throw new IllegalArgumentException("Page number must be positive.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Page title is required.");
        }
        if (title.trim().length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Page title must be 100 characters or less.");
        }
        if (description != null && description.trim().length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("Page description must be 500 characters or less.");
        }
        if (sortOrder == null || sortOrder < 0) {
            throw new IllegalArgumentException("Page sort order must be zero or positive.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
