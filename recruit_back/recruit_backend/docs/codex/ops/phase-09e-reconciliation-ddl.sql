-- Phase 09e — Reconciliation + 안정화 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 반영. 운영 DB에만 적용.
-- 전제: phase-09c / phase-09d-1 / phase-09d-2 DDL 적용 완료.

-- 1) application_attachment — 바이너리 삭제 실패의 row 수준 사유(09e Low 2, reconciliation 추적용)
--    sanitized 코드만 저장(PII·경로 원문 미포함): EMPTY_STORAGE_PATH / STILL_EXISTS / INVALID_STORAGE_PATH /
--    DELETE_FAILED / EXCEPTION / UNKNOWN. 성공(BINARY_DELETED) 시 NULL 로 해소.
ALTER TABLE application_attachment ADD COLUMN binary_delete_failure_code VARCHAR(100) NULL;

-- 신규 테이블/그 외 컬럼은 없음. AuditActionType.PURGE_RECONCILE / AttachmentStorageHealthIssueType
-- .PURGED_PHYSICAL_FILE_PRESENT 는 enum(애플리케이션 코드)이라 DDL 불요(VARCHAR 컬럼에 값만 추가됨).

-- 참고(09d-2 에서 예고된 2단계 마이그레이션 — 본 9e 와 독립, 1단계 코드 안정화 후 별도 시점에 실행):
-- UPDATE application_attachment SET physical_file_status = 'SOFT_DELETED' WHERE physical_file_status = 'DELETED';
-- SELECT COUNT(*) FROM application_attachment WHERE physical_file_status = 'DELETED';  -- 0 건 확인
