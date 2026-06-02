package com.shinyoung.recruit.enumeration;

/**
 * 전형 funnel 통계의 집계 축(dimension).
 *
 * <p>{@link #POSITION}(분야별, FK 기반 정확, 07c), {@link #SCHOOL}(학교별, 08c {@code schoolId} 최종학력 매칭, 08d),
 * {@link #CERTIFICATE}(자격명별 보유 지원자 distinct, free-text 정규화 + topN/'기타', 08e)을 지원한다. SCHOOL/POSITION 은
 * P 의 분할이지만 CERTIFICATE 는 그룹이 중복(한 지원자가 여러 자격 보유 시 여러 그룹)될 수 있다.
 */
public enum FunnelDimension {
    POSITION,
    SCHOOL,
    CERTIFICATE
}
