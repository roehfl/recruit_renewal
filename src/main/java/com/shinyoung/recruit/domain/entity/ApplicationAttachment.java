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
        name = "application_attachment",
        indexes = {
                @Index(name = "idx_application_attachment_application", columnList = "job_application_id"),
                @Index(name = "idx_application_attachment_sort", columnList = "job_application_id,sort_order"),
                @Index(name = "idx_application_attachment_type", columnList = "job_application_id,attachment_type"),
                @Index(name = "idx_application_attachment_section", columnList = "job_application_id,section_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType attachmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationSectionType sectionType;

    private Long sectionRecordId;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationAttachment(
            JobApplication jobApplication,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.attachmentType = attachmentType;
        this.sectionType = sectionType;
        this.sectionRecordId = sectionRecordId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
    }

    public static ApplicationAttachment create(
            JobApplication jobApplication,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder
    ) {
        return new ApplicationAttachment(
                jobApplication,
                attachmentType,
                sectionType,
                sectionRecordId,
                originalFileName,
                storedFileName,
                storagePath,
                contentType,
                fileSize,
                sortOrder
        );
    }
}
