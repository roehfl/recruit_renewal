package com.shinyoung.recruit.enumeration;

/** 시스템 자동발송 1통의 결과. ACCEPTED 솔루션 접수(또는 이미 성공), FAILED 접수 실패, NO_TEMPLATE 기본 템플릿 없음(보내지 않음). */
public enum SystemMailOutcome {
    ACCEPTED,
    FAILED,
    NO_TEMPLATE
}
