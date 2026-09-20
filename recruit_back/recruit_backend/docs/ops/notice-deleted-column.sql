-- 공지 soft delete(`notice.deleted`) 컬럼 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - 신규/개발 H2(create-drop, jdbc:h2:mem 또는 새 파일 DB)에서는 `deleted` 컬럼이 자동 생성되므로 본 SQL은 불필요하다.
--   - 기존 데이터가 있는 영속 DB(운영 후보 MariaDB, 또는 기존 행이 쌓인 dev H2 파일 DB)에는
--     아래 DDL을 1회 수동 반영해야 한다. (엔티티는 `@Column(nullable = false) private boolean deleted = false;`)
--
-- 주의
--   - 기존 공지는 전부 삭제되지 않은 상태(0)로 backfill된다. 적용 후 애플리케이션을 재기동한다.
--   - deleted = 1 인 공지는 지원자 화면(GET /api/board/notices, /{id})에서 빠지고
--     관리자 목록(GET /api/admin/notices)에만 "삭제됨"으로 남는다.
--   - 적용 전 백업을 권장한다.

-- MariaDB (운영 후보) / H2 (MODE=MySQL)
ALTER TABLE notice
    ADD COLUMN deleted BIT NOT NULL DEFAULT 0;

-- 참고: DEFAULT 적용이 다른 환경이면 아래로 분리 적용한다.
-- ALTER TABLE notice ADD COLUMN deleted BIT;
-- UPDATE notice SET deleted = 0 WHERE deleted IS NULL;
-- ALTER TABLE notice ALTER COLUMN deleted BIT NOT NULL;
