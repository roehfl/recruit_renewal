-- 권한 관리 슬라이스: user_role_mapping(사용자별 role 매핑) 테이블 수동 반영 DDL
--
-- 배경
--   - 본 프로젝트는 Flyway/Liquibase 등 migration framework를 사용하지 않는다(스키마는 Hibernate ddl-auto 생성).
--   - 신규/개발 H2에서는 `user_role_mapping` 테이블이 자동 생성되므로 본 SQL은 불필요하다.
--   - 운영 후보 DB(MariaDB) 등 ddl-auto를 validate/none으로 운영하는 환경에는 아래 DDL을 1회 수동 반영해야 한다.
--   - 엔티티 기준: `com.shinyoung.recruit.domain.entity.UserRoleMapping` (BaseEntity 상속).
--
-- 주의
--   - login_id 는 users FK가 아니라 문자열이다. 임직원은 최초 로그인 시 JIT 생성되므로,
--     아직 로그인한 적 없는 직원에게도 미리 권한(예: ROLE_INTERVIEWER)을 걸 수 있어야 하기 때문이다.
--   - (login_id, role_name) 중복은 DB unique 제약 없이 서비스(RoleMappingService)에서 검증한다
--     (dept_role_mapping 관례와 동일).
--   - role_name 은 ROLE_ 접두어를 포함한 완전한 authority 문자열이다(RoleNames 참조).
--   - 적용 전 백업을 권장한다.

-- MariaDB (운영 후보) / H2 (MODE=MariaDB)
create table user_role_mapping (
    id bigint not null auto_increment primary key,
    login_id varchar(255),
    role_name varchar(255),
    created_at datetime,
    updated_at datetime,
    created_by varchar(255),
    updated_by varchar(255)
);

-- 로그인 시 login_id 완전일치 조회 경로(CustomLdapUserDetailsMapper)용 인덱스
create index idx_user_role_mapping_login_id on user_role_mapping (login_id);
