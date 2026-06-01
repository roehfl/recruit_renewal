package com.shinyoung.recruit.enumeration;

/**
 * 전형 funnel 통계의 집계 축(dimension).
 *
 * <p>Phase 07c에서 확정된 축은 {@link #POSITION}(분야별, FK 기반 정확)뿐이다.
 * {@link #SCHOOL}/{@link #CERTIFICATE}는 free-text 부정확성으로 미확정이며, 활성화 전까지 호출 시
 * 명시적 미지원 응답으로 처리한다.
 */
public enum FunnelDimension {
    POSITION,
    SCHOOL,
    CERTIFICATE
}
