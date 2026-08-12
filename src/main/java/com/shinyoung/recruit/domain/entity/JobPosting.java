package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobPostingType postingType;

    @Column(length = 500)
    private String summary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(nullable = false)
    private LocalDateTime receptionStartDateTime;

    @Column(nullable = false)
    private LocalDateTime receptionEndDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobPostingStatus status;

    private LocalDateTime publishedAt;

    private LocalDateTime closedAt;

    /**
     * retentionAnchorAt 소스(Phase 09c). "공고 마감(closedAt)" ≠ "채용 프로세스 종료" 이므로 자동 세팅하지
     * 않고 관리자 수동 anchor 명령으로만 확정한다(설계 §5.3, 리뷰 2차 #5). null 이면 retention scan 에서
     * {@code ANCHOR_NOT_FIXED} SKIP. {@code closedAt} 사용은 정책 {@code baselineType=CLOSED_AT} 명시 시에만.
     */
    private LocalDateTime hiringEndedAt;

    private LocalDateTime displayStartDateTime;

    private LocalDateTime displayEndDateTime;

    @Column(nullable = false)
    private boolean visible;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<JobPosition> jobPositions = new ArrayList<>();

    @OneToOne(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private ApplicationFormConfig applicationFormConfig;

    private JobPosting(
            String title,
            JobPostingType postingType,
            String summary,
            String contentHtml,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            Boolean visible,
            Boolean pinned,
            Integer displayOrder
    ) {
        this.title = title;
        this.postingType = defaultPostingType(postingType);
        this.summary = summary;
        this.contentHtml = contentHtml;
        this.receptionStartDateTime = receptionStartDateTime;
        this.receptionEndDateTime = receptionEndDateTime;
        this.status = JobPostingStatus.DRAFT;
        this.displayStartDateTime = displayStartDateTime;
        this.displayEndDateTime = displayEndDateTime;
        this.visible = visible == null || visible;
        this.pinned = pinned != null && pinned;
        this.displayOrder = defaultDisplayOrder(displayOrder);
    }

    public static JobPosting create(String title, String contentHtml, LocalDateTime receptionStartDateTime, LocalDateTime receptionEndDateTime) {
        return new JobPosting(
                title,
                null,
                null,
                contentHtml,
                receptionStartDateTime,
                receptionEndDateTime,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static JobPosting create(
            String title,
            JobPostingType postingType,
            String summary,
            String contentHtml,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            Boolean visible,
            Boolean pinned,
            Integer displayOrder
    ) {
        return new JobPosting(
                title,
                postingType,
                summary,
                contentHtml,
                receptionStartDateTime,
                receptionEndDateTime,
                displayStartDateTime,
                displayEndDateTime,
                visible,
                pinned,
                displayOrder
        );
    }

    public void updateBasicInfo(String title, String contentHtml, LocalDateTime receptionStartDateTime, LocalDateTime receptionEndDateTime) {
        updateBasicInfo(
                title,
                this.postingType,
                this.summary,
                contentHtml,
                receptionStartDateTime,
                receptionEndDateTime,
                this.displayStartDateTime,
                this.displayEndDateTime,
                this.visible,
                this.pinned,
                this.displayOrder
        );
    }

    public void updateBasicInfo(
            String title,
            JobPostingType postingType,
            String summary,
            String contentHtml,
            LocalDateTime receptionStartDateTime,
            LocalDateTime receptionEndDateTime,
            LocalDateTime displayStartDateTime,
            LocalDateTime displayEndDateTime,
            Boolean visible,
            Boolean pinned,
            Integer displayOrder
    ) {
        this.title = title;
        this.postingType = defaultPostingType(postingType);
        this.summary = summary;
        this.contentHtml = contentHtml;
        this.receptionStartDateTime = receptionStartDateTime;
        this.receptionEndDateTime = receptionEndDateTime;
        this.displayStartDateTime = displayStartDateTime;
        this.displayEndDateTime = displayEndDateTime;
        this.visible = visible == null || visible;
        this.pinned = pinned != null && pinned;
        this.displayOrder = defaultDisplayOrder(displayOrder);
    }

    public void replaceJobPositions(List<JobPosition> positions) {
        this.jobPositions.clear();
        for (JobPosition position : positions) {
            position.assignJobPosting(this);
            this.jobPositions.add(position);
        }
    }

    public void updateApplicationFormConfig(ApplicationFormConfig config) {
        if (this.applicationFormConfig != null) {
            this.applicationFormConfig.update(
                    config.isUseEducation(),
                    config.isRequireEducation(),
                    config.isUseCareer(),
                    config.isRequireCareer(),
                    config.isUseCertificate(),
                    config.isRequireCertificate(),
                    config.isUseLanguage(),
                    config.isRequireLanguage(),
                    config.isUseMilitary(),
                    config.isRequireMilitary(),
                    config.isUseAward(),
                    config.isRequireAward(),
                    config.isUseGapPeriod(),
                    config.isRequireGapPeriod(),
                    config.isUseAttachment()
            );
            return;
        }
        config.assignJobPosting(this);
        this.applicationFormConfig = config;
    }

    public void publish(LocalDateTime now) {
        this.status = JobPostingStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void close(LocalDateTime now) {
        this.status = JobPostingStatus.CLOSED;
        this.closedAt = now;
    }

    /** 채용 프로세스 종료 시점(retention anchor) 수동 확정(Phase 09c). 정정 재확정 허용. */
    public void fixHiringEndedAt(LocalDateTime hiringEndedAt) {
        this.hiringEndedAt = hiringEndedAt;
    }

    private static JobPostingType defaultPostingType(JobPostingType postingType) {
        return postingType == null ? JobPostingType.PUBLIC_RECRUITMENT : postingType;
    }

    private static Integer defaultDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }
}
