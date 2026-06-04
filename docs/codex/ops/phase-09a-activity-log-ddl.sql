-- Phase 09a: ActivityLog(영속 감사 증적) 테이블 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - 신규/개발 H2(create-drop, jdbc:h2:mem 또는 새 파일 DB)에서는 `activity_log` 테이블이 자동 생성되므로 본 SQL은 불필요하다.
--   - 운영 후보 DB(MariaDB) 등 ddl-auto를 validate/none으로 운영하는 환경에는 아래 DDL을 1회 수동 반영해야 한다.
--   - 엔티티 기준: `com.shinyoung.recruit.domain.entity.ActivityLog` (append-only, BaseEntity 미상속).
--
-- 주의
--   - append-only 테이블이다. UPDATE/DELETE 권한을 애플리케이션 계정에 줄 필요가 없다(INSERT/SELECT만으로 충분).
--   - 컬럼 길이는 엔티티 @Column(length)와 일치해야 한다. 변경 시 ActivityLogService의 truncate 상수도 함께 변경한다.
--   - 적용 전 백업을 권장한다.

-- MariaDB (운영 후보) / H2 (MODE=MariaDB)
create table activity_log (
    id bigint not null auto_increment primary key,
    occurred_at datetime not null,
    actor_type varchar(20) not null,
    actor_id varchar(100),
    actor_role_snapshot varchar(255),
    action_type varchar(50) not null,
    action_result varchar(20) not null,
    target_type varchar(40) not null,
    target_id varchar(100),
    job_posting_id bigint,
    application_id bigint,
    applicant_ref_hash varchar(128),
    reason_code varchar(40),
    reason_message varchar(1000),
    correlation_id varchar(100),
    trace_id varchar(100),
    ip_address varchar(64),
    user_agent varchar(512),
    metadata_json longtext
);

-- 인덱스 (엔티티 @Table(indexes)와 동일 — 7종)
create index idx_activity_log_occurred_at on activity_log (occurred_at);
create index idx_activity_log_actor_id on activity_log (actor_id);
create index idx_activity_log_target on activity_log (target_type, target_id);
create index idx_activity_log_application_id on activity_log (application_id);
create index idx_activity_log_job_posting_id on activity_log (job_posting_id);
create index idx_activity_log_action_type on activity_log (action_type);
-- 9b read API: actionResult(FAILURE/DENIED/CONFLICT) + 기간 조건 동시 검색 대비(9a 리뷰 보완)
create index idx_activity_log_action_result_occurred on activity_log (action_result, occurred_at);
