package com.shinyoung.recruit.enumeration;

/**
 * Excel upload commit 전체 결과. all-or-nothing이므로 부분 성공은 없다.
 *
 * <ul>
 *     <li>{@code APPLIED} - 전 행 검증 통과, 변경 행을 단일 transaction에서 적용</li>
 *     <li>{@code REJECTED_VALIDATION} - 형식/허용값/교차검증 오류 행이 하나라도 있어 전체 거부(0건 적용)</li>
 *     <li>{@code REJECTED_STALE} - 낙관적 동시성 위반(STALE) 행이 있어 전체 거부(0건 적용)</li>
 * </ul>
 */
public enum StageResultUploadCommitOutcome {
    APPLIED,
    REJECTED_VALIDATION,
    REJECTED_STALE
}
