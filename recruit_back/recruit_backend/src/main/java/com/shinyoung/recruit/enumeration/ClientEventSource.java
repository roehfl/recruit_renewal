package com.shinyoung.recruit.enumeration;

/**
 * Client event 발생 채널(Phase 09f). public 수집 API는 APPLICANT_WEB만 허용한다(설계 6.1, 리뷰 Blocker 1)
 * — ADMIN_WEB은 enum에만 존재하며, 향후 별도 admin-authenticated 수집 endpoint에서만 받는다.
 */
public enum ClientEventSource {
    APPLICANT_WEB,
    ADMIN_WEB
}
