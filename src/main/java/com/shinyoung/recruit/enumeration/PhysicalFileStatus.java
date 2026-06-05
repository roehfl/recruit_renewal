package com.shinyoung.recruit.enumeration;

import java.util.List;

/**
 * 첨부 물리 파일 상태(Phase 09d-2 재정의 — PII 인벤토리 §8, 리뷰 #4).
 *
 * <p><b>3단계 안전 마이그레이션 1단계</b>(리뷰 3차 #1): {@code DELETED}(legacy)와 {@code SOFT_DELETED} 를
 * <b>둘 다 유지</b>하고 동일(soft-deleted)하게 취급한다. {@code markDeleted()} 는 이제 {@code SOFT_DELETED} 를
 * 기록한다. 2단계 = 운영 수동 DDL {@code UPDATE … 'DELETED'→'SOFT_DELETED'} + 잔존 0건 확인,
 * 3단계(후속 phase) = {@code DELETED} enum 제거.
 *
 * <p>purge saga(9d-2)는 {@code BINARY_DELETE_*} 만 사용 — soft-delete 와 의미 분리.
 */
public enum PhysicalFileStatus {
    /** 파일 미업로드, metadata 만. */
    METADATA_ONLY,
    /** 파일 존재. */
    STORED,
    /** DB 는 있으나 파일 없음(스캔 확인). */
    MISSING,
    /** legacy soft-delete(1단계 호환 유지 — SOFT_DELETED 와 동일 취급, 3단계에서 제거). */
    DELETED,
    /** 사용자/관리자 soft delete(기존 DELETED 개명). */
    SOFT_DELETED,
    /** 파기 saga ① 완료, 물리 삭제 대기. */
    BINARY_DELETE_PENDING,
    /** 파기 saga 물리 소멸 확인 완료(storagePath null). */
    BINARY_DELETED,
    /** 물리 삭제 실패(재시도/reconciliation 대상 — 9e). */
    BINARY_DELETE_FAILED;

    /** soft-deleted 동일 취급 집합(1단계 마이그레이션). */
    public static final List<PhysicalFileStatus> SOFT_DELETED_FAMILY = List.of(DELETED, SOFT_DELETED);

    /** 지원자/관리자 목록·활성 판정에서 숨겨야 하는 상태(soft-deleted + purge lifecycle 전부). */
    public static final List<PhysicalFileStatus> HIDDEN_FROM_LISTING = List.of(
            DELETED, SOFT_DELETED, BINARY_DELETE_PENDING, BINARY_DELETED, BINARY_DELETE_FAILED);

    /** 바이너리 소멸이 확인되지 않은 상태(PURGED 승격 금지 — saga 처리 대상). */
    public static final List<PhysicalFileStatus> BINARY_OUTSTANDING = List.of(
            STORED, DELETED, SOFT_DELETED, BINARY_DELETE_PENDING, BINARY_DELETE_FAILED);
}
