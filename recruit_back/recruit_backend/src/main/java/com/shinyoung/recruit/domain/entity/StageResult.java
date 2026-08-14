package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.exception.InvalidStageResultException;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@Table(
        name = "stage_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stage_result_stage_application",
                        columnNames = {"stage_id", "job_application_id"}
                )
        },
        indexes = {
                @Index(name = "idx_stage_result_stage", columnList = "stage_id"),
                @Index(name = "idx_stage_result_job_application", columnList = "job_application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageResultStatus resultStatus;

    private BigDecimal score;

    @Column(length = 2000)
    private String comment;

    private LocalDateTime decidedAt;

    private String decidedBy;

    /**
     * 낙관적 잠금 버전. 모든 StageResult write 경로(수동 updateResult/bulkUpdateResults, Excel upload commit)
     * 간의 lost update를 방지한다. 충돌 시 flush에서 {@code ObjectOptimisticLockingFailureException}이 발생하고
     * 409로 매핑된다. Excel upload는 이에 더해 PESSIMISTIC_WRITE + 토큰으로 upload-vs-upload 경쟁에 row-level
     * STALE 피드백을 제공한다.
     *
     * <p>이 컬럼은 신규/개발 H2(create-drop)에서는 Hibernate가 자동 생성하지만, 기존 데이터가 있는 영속 DB에는
     * 수동 DDL 반영이 필요하다: {@code docs/codex/ops/phase-07d-stage-result-version-column.sql}.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    private StageResult(Stage stage, JobApplication jobApplication) {
        validateSameJobPosting(stage, jobApplication);
        this.stage = stage;
        this.jobApplication = jobApplication;
        this.resultStatus = StageResultStatus.PENDING;
    }

    public static StageResult initialize(Stage stage, JobApplication jobApplication) {
        return new StageResult(stage, jobApplication);
    }

    public void updateResult(
            StageResultStatus resultStatus,
            BigDecimal score,
            String comment,
            LocalDateTime decidedAt,
            String decidedBy
    ) {
        validateResultStatus(resultStatus);
        validateComment(comment);
        if (decidedAt == null) {
            throw new InvalidStageResultException("DecidedAt is required.");
        }
        this.resultStatus = resultStatus;
        this.score = score;
        this.comment = comment;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    private static void validateSameJobPosting(Stage stage, JobApplication jobApplication) {
        if (stage == null) {
            throw new InvalidStageResultException("Stage is required.");
        }
        if (jobApplication == null) {
            throw new InvalidStageResultException("JobApplication is required.");
        }
        Long stageJobPostingId = stage.getJobPosting() == null ? null : stage.getJobPosting().getId();
        Long applicationJobPostingId = jobApplication.getJobPosting() == null ? null : jobApplication.getJobPosting().getId();
        if (stageJobPostingId == null || applicationJobPostingId == null
                || !Objects.equals(stageJobPostingId, applicationJobPostingId)) {
            throw new InvalidStageResultException("Stage and JobApplication must belong to the same JobPosting.");
        }
    }

    private static void validateResultStatus(StageResultStatus resultStatus) {
        if (resultStatus == null) {
            throw new InvalidStageResultException("StageResult status is required.");
        }
        if (resultStatus == StageResultStatus.PENDING) {
            throw new InvalidStageResultException("StageResult cannot be changed back to PENDING.");
        }
    }

    private static void validateComment(String comment) {
        if (comment != null && comment.length() > 2000) {
            throw new InvalidStageResultException("StageResult comment must be 2000 characters or less.");
        }
    }
}
