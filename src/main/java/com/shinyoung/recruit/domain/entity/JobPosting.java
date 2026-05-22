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
    @Column(nullable = false, columnDefinition = "LONGTEXT")
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
                    config.isUseCareer(),
                    config.isUseCertificate(),
                    config.isUseLanguage(),
                    config.isUseMilitary(),
                    config.isUseAward(),
                    config.isUseGapPeriod()
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

    private static JobPostingType defaultPostingType(JobPostingType postingType) {
        return postingType == null ? JobPostingType.PUBLIC_RECRUITMENT : postingType;
    }

    private static Integer defaultDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }
}
