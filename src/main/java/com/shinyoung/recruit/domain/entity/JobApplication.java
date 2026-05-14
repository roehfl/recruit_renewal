package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_application",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_application_applicant_job_posting",
                        columnNames = {"applicant_id", "job_posting_id"}
                )
        },
        indexes = {
                @Index(name = "idx_job_application_applicant", columnList = "applicant_id"),
                @Index(name = "idx_job_application_job_posting", columnList = "job_posting_id"),
                @Index(name = "idx_job_application_job_position", columnList = "job_position_id"),
                @Index(name = "idx_job_application_status", columnList = "status"),
                @Index(name = "idx_job_application_job_posting_status", columnList = "job_posting_id,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobApplicationStatus status;

    private LocalDateTime submittedAt;

    private LocalDateTime withdrawnAt;

    @Column(nullable = false)
    private String applicantNameSnapshot;

    @Column(nullable = false)
    private String jobPostingTitleSnapshot;

    @Column(nullable = false)
    private String jobPositionNameSnapshot;

    private JobApplication(
            Applicant applicant,
            JobPosting jobPosting,
            JobPosition jobPosition,
            String applicantNameSnapshot,
            String jobPostingTitleSnapshot,
            String jobPositionNameSnapshot
    ) {
        this.applicant = applicant;
        this.jobPosting = jobPosting;
        this.jobPosition = jobPosition;
        this.status = JobApplicationStatus.DRAFT;
        this.applicantNameSnapshot = applicantNameSnapshot;
        this.jobPostingTitleSnapshot = jobPostingTitleSnapshot;
        this.jobPositionNameSnapshot = jobPositionNameSnapshot;
    }

    public static JobApplication create(
            Applicant applicant,
            JobPosting jobPosting,
            JobPosition jobPosition,
            String applicantNameSnapshot,
            String jobPostingTitleSnapshot,
            String jobPositionNameSnapshot
    ) {
        return new JobApplication(
                applicant,
                jobPosting,
                jobPosition,
                applicantNameSnapshot,
                jobPostingTitleSnapshot,
                jobPositionNameSnapshot
        );
    }
}
