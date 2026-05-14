package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "stage",
        indexes = {
                @Index(name = "idx_stage_job_posting", columnList = "job_posting_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private String stageName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageType stageType;

    @Column(nullable = false)
    private Integer stageOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageStatus status;

    private LocalDateTime resultAnnouncementDateTime;

    @Column(nullable = false)
    private boolean finalStage;

    private Stage(
            JobPosting jobPosting,
            String stageName,
            StageType stageType,
            Integer stageOrder,
            LocalDateTime resultAnnouncementDateTime,
            boolean finalStage
    ) {
        this.jobPosting = jobPosting;
        this.stageName = stageName;
        this.stageType = stageType;
        this.stageOrder = stageOrder;
        this.status = StageStatus.READY;
        this.resultAnnouncementDateTime = resultAnnouncementDateTime;
        this.finalStage = finalStage;
    }

    public static Stage create(
            JobPosting jobPosting,
            String stageName,
            StageType stageType,
            Integer stageOrder,
            LocalDateTime resultAnnouncementDateTime,
            boolean finalStage
    ) {
        return new Stage(jobPosting, stageName, stageType, stageOrder, resultAnnouncementDateTime, finalStage);
    }

    public void update(
            String stageName,
            StageType stageType,
            Integer stageOrder,
            LocalDateTime resultAnnouncementDateTime,
            boolean finalStage
    ) {
        this.stageName = stageName;
        this.stageType = stageType;
        this.stageOrder = stageOrder;
        this.resultAnnouncementDateTime = resultAnnouncementDateTime;
        this.finalStage = finalStage;
    }

    public void reorder(Integer stageOrder) {
        this.stageOrder = stageOrder;
    }

    public void start() {
        this.status = StageStatus.IN_PROGRESS;
    }

    public void announce() {
        this.status = StageStatus.RESULT_ANNOUNCED;
    }

    public void close() {
        this.status = StageStatus.CLOSED;
    }
}
