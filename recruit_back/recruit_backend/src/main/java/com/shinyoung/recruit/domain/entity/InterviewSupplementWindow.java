package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 관리자가 바꾼 지원자별 입력 가능 시간. 행이 없는 지원자는 조의 도착시간 ~ +2시간(기본값)을 쓴다.
 */
@Entity
@Getter
@Table(
        name = "interview_supplement_window",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_supplement_window_application",
                        columnNames = {"interview_supplement_id", "job_application_id"}
                )
        },
        indexes = {
                @Index(name = "idx_interview_supplement_window_application", columnList = "job_application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSupplementWindow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_supplement_id", nullable = false)
    private InterviewSupplement supplement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    private InterviewSupplementWindow(
            InterviewSupplement supplement,
            JobApplication jobApplication,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        this.supplement = supplement;
        this.jobApplication = jobApplication;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }

    public static InterviewSupplementWindow create(
            InterviewSupplement supplement,
            JobApplication jobApplication,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        return new InterviewSupplementWindow(supplement, jobApplication, startDateTime, endDateTime);
    }

    public void change(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
    }
}
