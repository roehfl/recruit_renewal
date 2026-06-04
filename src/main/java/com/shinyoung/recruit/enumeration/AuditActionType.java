package com.shinyoung.recruit.enumeration;

/**
 * ActivityLog 감사 행위 유형(Phase 09a). Java enum + DB VARCHAR 로 고정한다(운영 CommonCode 아님 —
 * taxonomy 는 애플리케이션 코드/테스트로 고정되어야 한다).
 *
 * <p>Phase 09a 는 foundation 이라 실제 emission(계측)은 없다. 아래 값은 09b~09e 에서 사용된다.
 */
public enum AuditActionType {
    // 정보 반출(09b, egress fail-close)
    EXPORT_APPLICATIONS,
    EXPORT_STAGE_RESULTS,
    EXPORT_INTERVIEWS,
    EXPORT_EVALUATIONS,
    APPLICATION_PDF,

    // 핵심 관리자 변경(09b)
    STAGE_RESULT_UPLOAD,
    STAGE_RESULT_CORRECT,
    STAGE_RESULT_ANNOUNCE,
    STAGE_RESULT_CONFIRM,
    EVALUATION_REOPEN,
    ATTACHMENT_ADMIN_DOWNLOAD,
    ATTACHMENT_ADMIN_DELETE,

    // 보존/파기(09c~09e)
    RETENTION_POLICY_UPDATE,
    RETENTION_HOLD_SET,
    RETENTION_HOLD_RELEASE,
    RETENTION_ANCHOR_SET,
    PURGE_SCAN,
    PURGE_EXECUTE
}
