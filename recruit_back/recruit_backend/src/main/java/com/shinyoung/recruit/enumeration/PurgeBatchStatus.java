package com.shinyoung.recruit.enumeration;

/**
 * PurgeBatch 상태(Phase 09c). batch 는 비원자 집계 컨테이너 — RUNNING→COMPLETED/PARTIAL_FAILED/FAILED 로
 * 전이한다(delete 금지 mutable ledger). FAILED 는 시작/criteria 생성 실패만(설계 §5.4).
 */
public enum PurgeBatchStatus {
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
