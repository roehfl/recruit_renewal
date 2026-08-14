package com.shinyoung.recruit.enumeration;

/**
 * 관리자 지원현황 조회의 최종학교조건 필터 값. 최종학력(최고 EducationLevel) 행 기준으로 판정한다.
 * DOMESTIC/OVERSEAS 는 countryCode 유무(국내는 null 또는 빈 문자열)로 구분한다.
 */
public enum FinalSchoolCondition {
    DOMESTIC,
    OVERSEAS,
    TRANSFER,
    BRANCH,
    NIGHT
}
