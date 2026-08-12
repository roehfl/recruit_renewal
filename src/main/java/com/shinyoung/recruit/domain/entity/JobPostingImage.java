package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private String originalFileName;

    /** storage root 기준 상대 경로. 응답에 노출하지 않는다(첨부파일 규약과 동일). */
    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer sortOrder;

    /** 대체 텍스트(웹접근성). 필수. */
    @Column(nullable = false, length = 200)
    private String altText;

    private JobPostingImage(
            JobPosting jobPosting,
            String originalFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder,
            String altText
    ) {
        this.jobPosting = jobPosting;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
        this.altText = altText;
    }

    public static JobPostingImage create(
            JobPosting jobPosting,
            String originalFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder,
            String altText
    ) {
        return new JobPostingImage(jobPosting, originalFileName, storagePath, contentType, fileSize, sortOrder, altText);
    }

    public void updateAltText(String altText) {
        this.altText = altText;
    }

    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
