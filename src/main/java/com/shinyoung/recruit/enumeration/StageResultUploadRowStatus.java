package com.shinyoung.recruit.enumeration;

/**
 * Excel upload(StageResult) 행 단위 검증/적용 상태.
 *
 * <ul>
 *     <li>{@code CHANGED} - 검증 통과 + 현재 값과 달라 commit 적용 대상</li>
 *     <li>{@code UNCHANGED} - 검증 통과 + 현재 값과 동일해 commit 제외</li>
 *     <li>{@code ERROR} - 형식/허용값/3중 교차검증 실패 행</li>
 *     <li>{@code STALE} - commit 시점에 DB {@code StageResult.updatedAt}과 토큰 불일치(낙관적 동시성 위반)</li>
 * </ul>
 */
public enum StageResultUploadRowStatus {
    CHANGED,
    UNCHANGED,
    ERROR,
    STALE
}
