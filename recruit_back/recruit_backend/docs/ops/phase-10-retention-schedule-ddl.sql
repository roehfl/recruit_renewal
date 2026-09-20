-- Phase 10 — 자동 파기 스케줄 설정(단일 행) 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 생성된다. 운영 DB에만 적용.
-- 적용 전: 동일 이름 테이블/컬럼/인덱스 존재 여부 확인(INFORMATION_SCHEMA).
-- 컬럼 타입은 운영 DB의 기존 관례(BIGINT PK, DATETIME(6), VARCHAR enum)에 맞춘다.

-- 1) retention_schedule_setting (단일 행 — id 고정 1)
CREATE TABLE retention_schedule_setting (
    id                BIGINT       NOT NULL PRIMARY KEY,
    enabled           BIT          NOT NULL,
    last_run_at       DATETIME(6)  NULL,
    last_run_result   VARCHAR(30)  NULL,
    last_run_batch_id BIGINT       NULL,
    updated_by        VARCHAR(100) NULL,
    updated_at        DATETIME(6)  NULL
);

-- 2) 초기 행. 안전 기본값은 꺼짐이며, 운영에서는 보존 정책을 먼저 등록한 뒤 화면에서 켠다.
INSERT INTO retention_schedule_setting (id, enabled) VALUES (1, 0);
