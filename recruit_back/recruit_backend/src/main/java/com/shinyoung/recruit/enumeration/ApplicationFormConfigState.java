package com.shinyoung.recruit.enumeration;

/**
 * 공고별 지원서 설정 상태. 지원서 설정 현황판이 쓰는 계산 값이다.
 * 우선순위는 MISSING > RELAYOUT_REQUIRED > DEFAULT > OK 순으로 판정한다.
 */
public enum ApplicationFormConfigState {
    /** 지원서 항목 설정이 없다. 게시할 수 없다. */
    MISSING,
    /** 저장된 레이아웃의 배치 섹션이 현재 활성 섹션과 어긋난다. 지원자 form-page 조회가 실패한다. */
    RELAYOUT_REQUIRED,
    /** 레이아웃을 저장한 적이 없어 기본 레이아웃으로 동작 중이다. */
    DEFAULT,
    /** 저장된 레이아웃과 활성 섹션이 일치한다. */
    OK
}
