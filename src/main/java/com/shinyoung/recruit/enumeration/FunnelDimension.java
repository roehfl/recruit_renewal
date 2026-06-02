package com.shinyoung.recruit.enumeration;

/**
 * 전형 funnel 통계의 집계 축(dimension).
 *
 * <p>{@link #POSITION}(분야별, FK 기반 정확, 07c)과 {@link #SCHOOL}(학교별, 08c의 {@code schoolId} 최종학력
 * 매칭 기반, 08d)을 지원한다. {@link #CERTIFICATE}는 master 부재로 free-text 부정확성이 남아 미지원이며, 호출 시
 * 명시적 미지원 응답으로 처리한다.
 */
public enum FunnelDimension {
    POSITION,
    SCHOOL,
    CERTIFICATE
}
