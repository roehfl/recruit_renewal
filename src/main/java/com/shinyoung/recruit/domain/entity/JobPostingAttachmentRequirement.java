package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
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
        name = "job_posting_attachment_requirement",
        indexes = {
                @Index(name = "idx_attachment_requirement_posting", columnList = "job_posting_id"),
                @Index(name = "idx_attachment_requirement_required", columnList = "job_posting_id,required")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingAttachmentRequirement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AttachmentType attachmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationSectionType sectionType;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private Integer minCount;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    private JobPostingAttachmentRequirement(
            JobPosting jobPosting,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            boolean required,
            Integer minCount,
            Integer sortOrder,
            String displayName,
            String description
    ) {
        this.jobPosting = jobPosting;
        this.attachmentType = attachmentType;
        this.sectionType = sectionType;
        this.required = required;
        this.minCount = minCount;
        this.sortOrder = sortOrder;
        this.displayName = displayName;
        this.description = description;
    }

    public static JobPostingAttachmentRequirement create(
            JobPosting jobPosting,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            boolean required,
            Integer minCount,
            Integer sortOrder,
            String displayName,
            String description
    ) {
        return new JobPostingAttachmentRequirement(
                jobPosting,
                attachmentType,
                sectionType,
                required,
                minCount,
                sortOrder,
                displayName,
                description
        );
    }
}
