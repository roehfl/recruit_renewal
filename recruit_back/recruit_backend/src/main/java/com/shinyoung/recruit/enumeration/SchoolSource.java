package com.shinyoung.recruit.enumeration;

/**
 * 학교 검색 결과의 출처. {@code schoolCode}의 네임스페이스를 구분한다(코드 체계가 서로 다르다).
 */
public enum SchoolSource {

    /** NEIS 학교기본정보(초·중·고). 코드는 {@code SD_SCHUL_CODE}. */
    NEIS,

    /** 공공데이터포털 전국대학및전문대학정보표준데이터(전문대·대학·대학원). 학교 검색의 기본 출처다. */
    UNIV_INFO,

    /** 공공데이터포털 전국대학별학과정보표준데이터(학과 단위). 학교 검색에는 쓰지 않는다. */
    UNIV_DEPT
}
