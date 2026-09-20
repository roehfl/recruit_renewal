package com.shinyoung.recruit.enumeration;

/**
 * ActivityLog 감사 대상 유형(Phase 09a). Java enum + DB VARCHAR.
 */
public enum AuditTargetType {
    STAGE_RESULT,
    JOB_APPLICATION,
    APPLICATION_ATTACHMENT,
    INTERVIEW_EVALUATION,
    EXPORT_DATASET,
    APPLICATION_PDF,
    RETENTION_POLICY,
    RETENTION_HOLD,
    JOB_POSTING,
    PURGE_BATCH,
    /** 자동 파기 스케줄 설정(Phase 10) — on/off 토글 감사 대상. */
    RETENTION_SCHEDULE
}
