package com.shinyoung.recruit.service;

/**
 * 지원현황 엑셀에서 페이지 단위로 배치 조회하는 부가 데이터 단위. 선택 컬럼이 요구하는 섹션만 조회한다
 * (예: 경력 컬럼을 고르지 않으면 경력 조회를 하지 않는다). 지원사항 컬럼은 base projection 으로 충족하므로 섹션이 없다.
 */
public enum ApplicationExportSection {
    BASIC_INFO,
    MILITARY,
    EDUCATION,
    CAREER,
    CERTIFICATE,
    LANGUAGE,
    AWARD,
    GAP_PERIOD,
    STAGE_RESULT
}
