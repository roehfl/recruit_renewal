-- Fix: Employee.deptName 잔존 unique 인덱스 제거 DDL
--
-- 배경
--   - `Employee.deptName`에는 최초 구현 시 `@Column(unique = true)`가 붙어 있었다.
--     같은 부서의 두 번째 임직원이 LDAP JIT 생성될 때 unique 충돌로 저장이 실패해 로그인이 막히는 결함이라
--     2026-06-05(Phase 05y 리뷰 3차 후속)에 엔티티에서 제거했다.
--   - 본 프로젝트는 Flyway/Liquibase를 쓰지 않고 스키마를 Hibernate ddl-auto로 관리한다.
--     `ddl-auto: update`는 **제약/인덱스를 추가만 하고 삭제하지 않는다.**
--     따라서 엔티티 수정 이전에 스키마가 만들어진 영속 DB에는 unique 인덱스가 그대로 남아 있고,
--     애플리케이션을 최신 코드로 올려도 증상이 재현된다.
--
-- 적용 대상
--   - 필요:   운영 후보 MariaDB, 기존 행이 쌓인 dev H2 파일 DB (엔티티 수정 이전에 생성된 스키마)
--   - 불필요: 신규 H2 인메모리/create-drop (제약이 애초에 생성되지 않음)
--
-- 주의
--   - 인덱스명은 Hibernate가 자동 생성해서 환경마다 다르다(`UK_` + 해시). 반드시 1단계로 조회해 확인한 뒤 2단계를 실행한다.
--   - unique 제약을 푸는 방향이라 기존 데이터 때문에 실패하지 않는다. 그래도 적용 전 백업을 권장한다.
--   - `users.login_id`의 unique(`uk_users_login_id`, Phase 05y)는 **유지해야 한다.** 아래 조회는 employee 테이블만 본다.


-- ── 1단계: 실제 인덱스명 조회 ────────────────────────────────────────────────

-- MariaDB
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
  FROM INFORMATION_SCHEMA.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME   = 'employee'
   AND COLUMN_NAME  = 'dept_name';
-- NON_UNIQUE = 0 인 행이 제거 대상이다. 행이 없으면 이미 정리된 DB이므로 2단계는 건너뛴다.

-- H2
SELECT tc.CONSTRAINT_NAME, ic.COLUMN_NAME
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
  JOIN INFORMATION_SCHEMA.INDEX_COLUMNS ic
    ON ic.INDEX_NAME = tc.INDEX_NAME
 WHERE tc.TABLE_NAME       = 'EMPLOYEE'
   AND tc.CONSTRAINT_TYPE  = 'UNIQUE'
   AND ic.COLUMN_NAME      = 'DEPT_NAME';


-- ── 2단계: 조회된 이름으로 제거 ──────────────────────────────────────────────

-- MariaDB (1단계에서 나온 INDEX_NAME으로 치환)
-- ALTER TABLE employee DROP INDEX `UK_xxxxxxxxxxxxxxxxxxxxxxxxx`;

-- H2 (1단계에서 나온 CONSTRAINT_NAME으로 치환)
-- ALTER TABLE employee DROP CONSTRAINT "UK_xxxxxxxxxxxxxxxxxxxxxxxxx";


-- ── 3단계: 검증 ──────────────────────────────────────────────────────────────
--   - 1단계 조회를 다시 실행해 결과가 비었는지 확인한다.
--   - 같은 부서 임직원 2명이 각각 최초 로그인(JIT 생성)되는지 확인한다.
--     회귀 테스트: EmployeeRepositoryTest.동일_deptName_임직원_2명을_저장할_수_있다
