package com.shinyoung.recruit.enumeration;

/**
 * PurgeJobItem 상태(Phase 09c~09d).
 *
 * <p>dry-run(09c) 은 {@code ELIGIBLE}/{@code SKIPPED} 만 산출한다(무변경 preview).
 * execute(09d) 는 {@code PENDING}→{@code FAILED}(retry)→{@code PURGED} 로 전이한다(설계 §5.4).
 */
public enum PurgeItemStatus {
    // dry-run(09c)
    ELIGIBLE,
    SKIPPED,
    // execute(09d 슬롯)
    PENDING,
    PURGED,
    FAILED
}
