package com.shinyoung.recruit.enumeration;

/** Client event 유형(Phase 09f). 수집 ROI가 높은 오류/핵심 checkpoint만 정의한다 — clickstream 아님. */
public enum ClientEventType {
    PAGE_OPENED,
    CHECKPOINT,

    API_ERROR,
    API_TIMEOUT,
    NETWORK_ERROR,
    SESSION_EXPIRED,
    FORBIDDEN,

    JS_ERROR,
    UNHANDLED_REJECTION,

    APPLICATION_DRAFT_SAVE_FAILED,
    APPLICATION_SUBMIT_CLICKED,
    APPLICATION_SUBMIT_FAILED,
    ATTACHMENT_UPLOAD_FAILED,

    CLIENT_VALIDATION_FAILED
}
