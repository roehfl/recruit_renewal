package com.shinyoung.recruit.enumeration;

/** 본인확인 요청 1건의 진행 상태. */
public enum NiceVerificationStatus {
    /** 요청 발급 완료, 콜백 대기. */
    PENDING,
    /** 콜백 수신·복호화 성공. resultToken 교환 대기. */
    VERIFIED,
    /** 사용자 취소 또는 NICE 실패. */
    FAIL
}
