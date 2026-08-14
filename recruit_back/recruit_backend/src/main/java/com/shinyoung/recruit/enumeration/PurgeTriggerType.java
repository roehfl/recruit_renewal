package com.shinyoung.recruit.enumeration;

/**
 * PurgeBatch 트리거 유형(Phase 09c). Phase 09 파기는 {@code RETENTION}(보존기간 경과) 만 사용한다.
 * {@code DATA_SUBJECT_REQUEST}/{@code FORCED_PURGE} 는 forced purge 후속 설계용 enum 슬롯 —
 * endpoint/실행 로직은 만들지 않는다(설계 §4 범위 제외).
 */
public enum PurgeTriggerType {
    RETENTION,
    DATA_SUBJECT_REQUEST,
    FORCED_PURGE
}
