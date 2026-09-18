-- Phase 09c — Retention 모델 + dry-run 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 생성된다. 운영 DB에만 적용.
-- 적용 전: 동일 이름 테이블/컬럼/인덱스 존재 여부 확인(INFORMATION_SCHEMA).
-- 컬럼 타입은 운영 DB의 기존 관례(BIGINT PK, DATETIME(6), VARCHAR enum)에 맞춘다.

-- 1) retention_policy
CREATE TABLE retention_policy (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_posting_id         BIGINT       NULL,
    retention_period_days  INT          NOT NULL,
    baseline_type          VARCHAR(30)  NOT NULL,
    enabled                BIT          NOT NULL,
    effective_from         DATETIME(6)  NULL,
    effective_to           DATETIME(6)  NULL,
    created_at             DATETIME(6)  NULL,
    updated_at             DATETIME(6)  NULL,
    created_by             VARCHAR(255) NULL,
    updated_by             VARCHAR(255) NULL
);
CREATE INDEX idx_retention_policy_job_posting ON retention_policy (job_posting_id);

-- 2) retention_hold
CREATE TABLE retention_hold (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT        NOT NULL,
    reason         VARCHAR(1000) NOT NULL,
    held_by        VARCHAR(100)  NOT NULL,
    released_at    DATETIME(6)   NULL,
    released_by    VARCHAR(100)  NULL,
    created_at     DATETIME(6)   NULL,
    updated_at     DATETIME(6)   NULL,
    created_by     VARCHAR(255)  NULL,
    updated_by     VARCHAR(255)  NULL
);
CREATE INDEX idx_retention_hold_application ON retention_hold (application_id);
CREATE INDEX idx_retention_hold_released_at ON retention_hold (released_at);

-- 3) purge_batch (delete 금지 mutable ledger — 운영 절차상 DELETE 권한 부여 금지)
CREATE TABLE purge_batch (
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    mode                    VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    trigger_type            VARCHAR(30)  NOT NULL,
    scan_at                 DATETIME(6)  NOT NULL,
    started_at              DATETIME(6)  NOT NULL,
    completed_at            DATETIME(6)  NULL,
    requested_by            VARCHAR(100) NOT NULL,
    source_dry_run_batch_id BIGINT       NULL,
    total_count             BIGINT       NOT NULL,
    eligible_count          BIGINT       NOT NULL,
    skipped_count           BIGINT       NOT NULL,
    policy_conflict_count   BIGINT       NOT NULL,
    created_at              DATETIME(6)  NULL,
    updated_at              DATETIME(6)  NULL,
    created_by              VARCHAR(255) NULL,
    updated_by              VARCHAR(255) NULL
);
CREATE INDEX idx_purge_batch_mode_status ON purge_batch (mode, status);

-- 4) purge_job_item (delete 금지 mutable ledger)
CREATE TABLE purge_job_item (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purge_batch_id BIGINT       NOT NULL,
    application_id BIGINT       NOT NULL,
    job_posting_id BIGINT       NULL,
    status         VARCHAR(30)  NOT NULL,
    reason_code    VARCHAR(40)  NULL,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    created_by     VARCHAR(255) NULL,
    updated_by     VARCHAR(255) NULL,
    CONSTRAINT fk_purge_job_item_batch FOREIGN KEY (purge_batch_id) REFERENCES purge_batch (id)
);
CREATE INDEX idx_purge_job_item_batch ON purge_job_item (purge_batch_id);
CREATE INDEX idx_purge_job_item_application ON purge_job_item (application_id);

-- 5) job_posting — retention anchor(수동 확정 전 NULL = ANCHOR_NOT_FIXED SKIP)
ALTER TABLE job_posting ADD COLUMN hiring_ended_at DATETIME(6) NULL;

-- 6) job_application — 파기 marker(09c 도입, 쓰기는 09d-1)
ALTER TABLE job_application ADD COLUMN purge_batch_id BIGINT NULL;
ALTER TABLE job_application ADD COLUMN purge_result VARCHAR(30) NULL;
ALTER TABLE job_application ADD COLUMN purged_at DATETIME(6) NULL;
