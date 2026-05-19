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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "stage_result_correction_history",
        indexes = {
                @Index(name = "idx_stage_result_correction_history_result", columnList = "stage_result_id"),
                @Index(name = "idx_stage_result_correction_history_corrected_at", columnList = "corrected_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageResultCorrectionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_result_id", nullable = false)
    private StageResult stageResult;

    @Column(nullable = false)
    private LocalDateTime correctedAt;

    @Column(nullable = false, length = 100)
    private String correctedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageResultStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StageResultStatus newStatus;

    private BigDecimal previousScore;

    private BigDecimal newScore;

    @Column(length = 2000)
    private String previousComment;

    @Column(length = 2000)
    private String newComment;

    private LocalDateTime previousDecidedAt;

    private LocalDateTime newDecidedAt;

    private StageResultCorrectionHistory(
            StageResult stageResult,
            LocalDateTime correctedAt,
            String correctedBy,
            String reason,
            StageResultStatus previousStatus,
            StageResultStatus newStatus,
            BigDecimal previousScore,
            BigDecimal newScore,
            String previousComment,
            String newComment,
            LocalDateTime previousDecidedAt,
            LocalDateTime newDecidedAt
    ) {
        validateRequired(stageResult, correctedAt, correctedBy, reason, previousStatus, newStatus);
        this.stageResult = stageResult;
        this.correctedAt = correctedAt;
        this.correctedBy = correctedBy;
        this.reason = reason;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.previousScore = previousScore;
        this.newScore = newScore;
        this.previousComment = previousComment;
        this.newComment = newComment;
        this.previousDecidedAt = previousDecidedAt;
        this.newDecidedAt = newDecidedAt;
    }

    public static StageResultCorrectionHistory create(
            StageResult stageResult,
            LocalDateTime correctedAt,
            String correctedBy,
            String reason,
            StageResultStatus previousStatus,
            StageResultStatus newStatus,
            BigDecimal previousScore,
            BigDecimal newScore,
            String previousComment,
            String newComment,
            LocalDateTime previousDecidedAt,
            LocalDateTime newDecidedAt
    ) {
        return new StageResultCorrectionHistory(
                stageResult,
                correctedAt,
                correctedBy,
                reason,
                previousStatus,
                newStatus,
                previousScore,
                newScore,
                previousComment,
                newComment,
                previousDecidedAt,
                newDecidedAt
        );
    }

    private static void validateRequired(
            StageResult stageResult,
            LocalDateTime correctedAt,
            String correctedBy,
            String reason,
            StageResultStatus previousStatus,
            StageResultStatus newStatus
    ) {
        if (stageResult == null) {
            throw new InvalidStageResultException("StageResult is required.");
        }
        if (correctedAt == null) {
            throw new InvalidStageResultException("CorrectedAt is required.");
        }
        if (correctedBy == null || correctedBy.isBlank()) {
            throw new InvalidStageResultException("CorrectedBy is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidStageResultException("Correction reason is required.");
        }
        if (reason.length() > 1000) {
            throw new InvalidStageResultException("Correction reason must be 1000 characters or less.");
        }
        if (previousStatus == null || newStatus == null) {
            throw new InvalidStageResultException("Correction status snapshot is required.");
        }
    }
}
