package com.shinyoung.recruit.enumeration;

/**
 * ActivityLog 의 검색 가능한 사유 코드(Phase 09a). 실패/거부/충돌/스킵 이벤트의 분류에 쓰인다.
 * 사람이 읽는 메시지는 {@code ActivityLog.reasonMessage}(sanitized)로 분리한다.
 */
public enum AuditReasonCode {
    VERSION_MISMATCH,
    AUTH_DENIED,
    VALIDATION_FAILED,
    RETENTION_NOT_DUE,
    RETENTION_HOLD,
    ALREADY_PURGED,
    ANCHOR_NOT_FIXED,
    APPLICATION_NOT_TERMINAL,
    INVALID_STAGE_CONFIGURATION,
    POLICY_NOT_FOUND,
    POLICY_CONFLICT,
    BINARY_DELETE_FAILED
}
