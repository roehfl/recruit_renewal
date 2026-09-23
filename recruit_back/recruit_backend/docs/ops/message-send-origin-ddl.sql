-- 시스템 자동발송 메일(2026-09-23): 메시지 종류 3개 추가 · message_send 발송 구분(origin) 추가 · 공고 없는 발송 허용 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - Hibernate 는 @Enumerated(STRING) 컬럼을 H2·MariaDB 모두 네이티브 ENUM('값', ...) 타입으로 만든다.
--     ddl-auto=update 는 기존 ENUM 컬럼의 값 목록을 늘리지 않는다. 기존 DB에 아래를 반영하지 않으면
--     새 종류(SIGNUP_VERIFICATION 등)를 넣는 순간 실패하고, 기동 시 시스템 기본 템플릿 생성
--     (SystemMessageTemplateInitializer)이 실패해 애플리케이션이 뜨지 않는다.
--   - 신규 DB(create-drop, 새 파일 DB)에서는 엔티티대로 자동 생성되므로 본 SQL은 불필요하다.
--   - 기존 데이터가 있는 영속 DB(운영 MariaDB, 기존 행이 쌓인 dev H2 파일 DB)에는 **새 버전 기동 전에** 1회 수동 반영한다.
--
-- 주의
--   - ENUM 값 목록은 기존 순서를 유지하고 새 값을 끝에 붙인다.
--   - 기존 발송 이력은 모두 관리자 발송이므로 origin 은 'ADMIN' 으로 채운다.
--   - 가입 인증·비밀번호 재설정 메일 이력은 공고가 없어 job_posting_id 가 NULL 이다.
--   - 적용 전 백업을 권장한다.

-- ============================================================
-- MariaDB (운영)
-- ============================================================
ALTER TABLE message_template
    MODIFY COLUMN message_type ENUM('RESULT_ANNOUNCEMENT','DEADLINE_REMINDER','INTERVIEW_SCHEDULE','INTERVIEW_NOTICE','FREE',
                                    'SIGNUP_VERIFICATION','PASSWORD_RESET','APPLICATION_SUBMITTED') NOT NULL;

ALTER TABLE message_send
    MODIFY COLUMN message_type ENUM('RESULT_ANNOUNCEMENT','DEADLINE_REMINDER','INTERVIEW_SCHEDULE','INTERVIEW_NOTICE','FREE',
                                    'SIGNUP_VERIFICATION','PASSWORD_RESET','APPLICATION_SUBMITTED') NOT NULL;

ALTER TABLE message_send
    ADD COLUMN origin ENUM('ADMIN','SYSTEM') NOT NULL DEFAULT 'ADMIN';

ALTER TABLE message_send
    MODIFY COLUMN job_posting_id BIGINT NULL;

-- ============================================================
-- H2 (dev 파일 DB, MODE 지정 없음)
-- ============================================================
-- ALTER TABLE message_template ALTER COLUMN message_type SET DATA TYPE
--     ENUM('RESULT_ANNOUNCEMENT','DEADLINE_REMINDER','INTERVIEW_SCHEDULE','INTERVIEW_NOTICE','FREE',
--          'SIGNUP_VERIFICATION','PASSWORD_RESET','APPLICATION_SUBMITTED');
-- ALTER TABLE message_send ALTER COLUMN message_type SET DATA TYPE
--     ENUM('RESULT_ANNOUNCEMENT','DEADLINE_REMINDER','INTERVIEW_SCHEDULE','INTERVIEW_NOTICE','FREE',
--          'SIGNUP_VERIFICATION','PASSWORD_RESET','APPLICATION_SUBMITTED');
-- ALTER TABLE message_send ADD COLUMN origin ENUM('ADMIN','SYSTEM') DEFAULT 'ADMIN' NOT NULL;
-- ALTER TABLE message_send ALTER COLUMN job_posting_id SET NULL;
