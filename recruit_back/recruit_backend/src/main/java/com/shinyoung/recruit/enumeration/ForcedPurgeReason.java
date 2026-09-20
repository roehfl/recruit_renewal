package com.shinyoung.recruit.enumeration;

/**
 * 강제 파기 사유(Phase 10). 자유 텍스트를 받지 않는 이유 — ActivityLog 와 파기 대장은 파기 대상이 아니라
 * 거기에 들어간 지원자 PII 는 영구히 남는다. 상세 경위는 오프라인 접수 대장이 맡는다.
 */
public enum ForcedPurgeReason {
    /** 본인(정보주체)의 삭제 요청. */
    DATA_SUBJECT_REQUEST,
    /** 중복 가입·오입력 계정 정리. */
    DUPLICATE_ACCOUNT,
    /** 그 밖의 사유. */
    OTHER
}
