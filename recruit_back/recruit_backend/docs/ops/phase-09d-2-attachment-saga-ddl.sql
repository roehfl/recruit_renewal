-- Phase 09d-2 — Attachment binary delete saga 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 반영. 운영 DB에만 적용.
-- 전제: phase-09c / phase-09d-1 DDL 적용 완료.

-- 1) application_attachment — saga 컬럼(인벤토리 §6)
ALTER TABLE application_attachment MODIFY storage_path VARCHAR(1000) NULL;   -- 최종 소멸(BINARY_DELETED) 시 null
ALTER TABLE application_attachment ADD COLUMN filename_hash    VARCHAR(128) NULL;
ALTER TABLE application_attachment ADD COLUMN binary_deleted_at DATETIME(6)  NULL;

-- 2) purge_batch — 바이너리 삭제 실패 집계(9d-2)
ALTER TABLE purge_batch ADD COLUMN binary_delete_failed_count BIGINT NOT NULL DEFAULT 0;

-- 3) PhysicalFileStatus 2단계 마이그레이션(인벤토리 §8 — 1단계 코드 배포·안정화 *후* 별도 시점에 실행)
--    실행 전: 1단계 코드(DELETED·SOFT_DELETED 동일 취급)가 배포되어 있어야 한다.
-- UPDATE application_attachment SET physical_file_status = 'SOFT_DELETED' WHERE physical_file_status = 'DELETED';
--    실행 후 잔존 0건 확인:
-- SELECT COUNT(*) FROM application_attachment WHERE physical_file_status = 'DELETED';
--    (3단계: 잔존 0건 확인 후의 후속 phase 에서 enum 'DELETED' 제거.)
