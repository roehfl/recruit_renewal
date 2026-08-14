-- Phase 07d: StageResult 낙관적 잠금(@Version) 컬럼 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - 신규/개발 H2(create-drop, jdbc:h2:mem 또는 새 파일 DB)에서는 `version` 컬럼이 자동 생성되므로 본 SQL은 불필요하다.
--   - 기존 데이터가 있는 영속 DB(운영 후보 MariaDB, 또는 기존 행이 쌓인 dev H2 파일 DB)에는
--     아래 DDL을 1회 수동 반영해야 한다. (엔티티는 `@Version @Column(nullable = false) private Long version;`)
--
-- 주의
--   - 기존 행은 version 기본값 0으로 backfill된다(DEFAULT 0). 적용 후 애플리케이션을 재기동한다.
--   - 적용 전 백업을 권장한다.

-- MariaDB (운영 후보) / H2 (MODE=MariaDB)
ALTER TABLE stage_result
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 참고: 일부 H2 버전에서 DEFAULT 미지원/차이가 있으면 아래로 분리 적용한다.
-- ALTER TABLE stage_result ADD COLUMN version BIGINT;
-- UPDATE stage_result SET version = 0 WHERE version IS NULL;
-- ALTER TABLE stage_result ALTER COLUMN version BIGINT NOT NULL;
