package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 자동 파기 스케줄 설정(Phase 10). <b>단일 행</b>(id 고정 1)이다.
 *
 * <p>{@code RetentionPolicy.enabled} 를 재사용하지 않는 이유 — 정책을 끄면 적격성 판정이
 * {@code POLICY_NOT_FOUND} 가 되어 강제 파기 화면의 판정 표시와 감사 사유까지 오염된다.
 * "스케줄을 돌릴 것인가"와 "보존 정책이 무엇인가"는 다른 스위치다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "retention_schedule_setting")
public class RetentionScheduleSetting {

    /** 단일 행 고정 id. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_result", length = 30)
    private RetentionScheduleRunResult lastRunResult;

    @Column(name = "last_run_batch_id")
    private Long lastRunBatchId;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 행이 없을 때 쓰는 기본값 — 꺼짐(안전 기본값). 모르고 켜져 있는 것보다 꺼져 있는 편이 낫다. */
    public static RetentionScheduleSetting initial() {
        RetentionScheduleSetting setting = new RetentionScheduleSetting();
        setting.id = SINGLETON_ID;
        setting.enabled = false;
        return setting;
    }

    public void updateEnabled(boolean enabled, String updatedBy, LocalDateTime updatedAt) {
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void recordRun(RetentionScheduleRunResult result, Long batchId, LocalDateTime runAt) {
        this.lastRunResult = result;
        this.lastRunBatchId = batchId;
        this.lastRunAt = runAt;
    }
}
