package com.shinyoung.recruit.enumeration;

/**
 * JobApplication 의 파기 진행/완료 marker(Phase 09c 컬럼 도입, 쓰기는 09d).
 * {@code JobApplicationStatus} 와 직교(orthogonal) — 기존 status enum 은 불변(설계 §7.2).
 * {@code PURGED} = 관계형 PII 제거 <b>AND</b> 첨부 바이너리 소멸 확인까지 완료(설계 §5.4).
 */
public enum PurgeResult {
    PURGE_PENDING,
    PARTIAL_PENDING,
    PARTIAL_FAILED,
    PURGED
}
