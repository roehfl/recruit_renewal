package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.JobPostingStatus;
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

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<JobPosition> jobPositions = new ArrayList<>();

    @OneToOne(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private ApplicationFormConfig applicationFormConfig;

    private JobPosting(String title, String contentHtml, LocalDateTime receptionStartDateTime, LocalDateTime receptionEndDateTime) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.receptionStartDateTime = receptionStartDateTime;
        this.receptionEndDateTime = receptionEndDateTime;
        this.status = JobPostingStatus.DRAFT;
    }

    public static JobPosting create(String title, String contentHtml, LocalDateTime receptionStartDateTime, LocalDateTime receptionEndDateTime) {
        return new JobPosting(title, contentHtml, receptionStartDateTime, receptionEndDateTime);
    }

    public void updateBasicInfo(String title, String contentHtml, LocalDateTime receptionStartDateTime, LocalDateTime receptionEndDateTime) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.receptionStartDateTime = receptionStartDateTime;
        this.receptionEndDateTime = receptionEndDateTime;
    }

    public void replaceJobPositions(List<JobPosition> positions) {
        this.jobPositions.clear();
        for (JobPosition position : positions) {
            position.assignJobPosting(this);
            this.jobPositions.add(position);
        }
    }

    public void updateApplicationFormConfig(ApplicationFormConfig config) {
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
}
