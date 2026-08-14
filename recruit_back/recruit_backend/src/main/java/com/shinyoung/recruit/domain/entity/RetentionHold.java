package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보존 예외(manual hold, Phase 09c). hold 가 걸린 지원서는 retention 경과해도 파기 대상에서 제외된다
 * ({@code RETENTION_HOLD} SKIP).
 *
 * <p><b>Phase 9 는 관리자 수동 hold 만</b>(설계 §5.3, 리뷰 2차 #6) — 자동 onboarded-hold 는 현 도메인에
 * 근거가 없어 후속(별도 ApplicationHireStatus 도입 필요). release 는 행 삭제가 아니라 {@code releasedAt}
 * 마킹(증적 보존). hold 사유 원문은 이 도메인만 보유하고 ActivityLog 에는 남기지 않는다.
 */
@Entity
@Getter
@Table(
        name = "retention_hold",
        indexes = {
                @Index(name = "idx_retention_hold_application", columnList = "application_id"),
                @Index(name = "idx_retention_hold_released_at", columnList = "released_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetentionHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "held_by", nullable = false, length = 100)
    private String heldBy;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_by", length = 100)
    private String releasedBy;

    private RetentionHold(Long applicationId, String reason, String heldBy) {
        this.applicationId = applicationId;
        this.reason = reason;
        this.heldBy = heldBy;
    }

    public static RetentionHold create(Long applicationId, String reason, String heldBy) {
        return new RetentionHold(applicationId, reason, heldBy);
    }

    public void release(String releasedBy, LocalDateTime releasedAt) {
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
    }

    public boolean isActive() {
        return releasedAt == null;
    }
}
