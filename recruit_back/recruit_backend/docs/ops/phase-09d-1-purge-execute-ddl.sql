-- Phase 09d-1 — Purge execute core 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 반영된다. 운영 DB에만 적용.
-- 전제: phase-09c-retention-ddl.sql 적용 완료.

-- 1) NOT NULL date PII → nullable (PII 인벤토리 §9 ALTER_NULLABLE+NULLIFY 대상)
ALTER TABLE application_certificate MODIFY acquired_date DATE NULL;
ALTER TABLE application_language    MODIFY exam_date     DATE NULL;
ALTER TABLE application_award       MODIFY award_date    DATE NULL;
ALTER TABLE application_gap_period  MODIFY start_date    DATE NULL;
ALTER TABLE application_gap_period  MODIFY end_date      DATE NULL;
ALTER TABLE application_career      MODIFY start_date    DATE NULL;

-- 2) purge_batch — execute 집계 컬럼(9d-1)
ALTER TABLE purge_batch ADD COLUMN purged_count  BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purge_batch ADD COLUMN pending_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purge_batch ADD COLUMN failed_count  BIGINT NOT NULL DEFAULT 0;

-- 참고: PLACEHOLDER('__PURGED__') 대상 NOT NULL String 컬럼은 DDL 불요(인벤토리 §9).
-- ciHash 는 overwrite 방식(권장안 A)이라 DDL 불요. 첨부(storagePath nullable 화 등)는 9d-2 에서 적용.
