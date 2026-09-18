# Fix - Employee.deptName unique 제약 제거

## Phase Summary

- Date: 2026-06-05
- Work type: small fix phase (Phase 05y 구현 리뷰 3차 후속 권고)
- Goal: `Employee.deptName`의 `@Column(unique = true)` 제거 — 동일 부서 임직원의 LDAP JIT 최초 로그인 차단 리스크 해소.

## 배경 (문제)

- `Employee.deptName`에 `@Column(unique = true)`가 걸려 있었다.
- 같은 부서 임직원은 당연히 여러 명일 수 있다. JIT 경로(`RoutingAuthenticationProvider.processLdapAndJit`)는 LDAP에서 받은 `deptName`을 Employee에 그대로 저장하므로, **같은 부서의 두 번째 임직원이 최초 로그인하면 deptName unique 충돌로 JIT 저장이 실패**한다.
- 05y의 JIT race 복구는 이 경우를 "loginId race가 아닌 제약 위반"으로 정확히 전파(복구 안 함)하지만, 그 결과는 해당 임직원의 **로그인 실패**다 — 인증 안정성 결함.

## Implemented Scope

- `Employee.deptName`의 `@Column(unique = true)` 제거 (이제 일반 컬럼)
- `RoutingAuthenticationProvider`/`RoutingAuthenticationProviderTest`의 "(deptName unique 등)" 예시 주석 정리 (복구 semantics는 불변 — loginId race만 복구, 그 외 제약 위반은 전파)
- `EmployeeRepositoryTest`에 동일 deptName 임직원 2명 저장 검증 추가

## Not Implemented / Out of Scope

- JIT/인증 로직 변경 없음 — 05y 복구 로직 그대로 유효
- `DeptRoleMapping` 등 부서명 기반 역할 매핑 비변경(별개 테이블, unique 의존 없음)

## Changed Files

| File | Change |
|------|--------|
| `src/main/java/.../domain/entity/Employee.java` | `deptName`의 `@Column(unique = true)` 제거(+미사용 `Column` import 제거, 비-unique 사유 주석) |
| `src/main/java/.../security/auth/RoutingAuthenticationProvider.java` | 주석에서 deptName unique 예시 제거(로직 불변) |
| `src/test/java/.../domain/repository/EmployeeRepositoryTest.java` | `동일_deptName_임직원_2명을_저장할_수_있다` 추가 (2 cases) |
| `src/test/java/.../security/auth/RoutingAuthenticationProviderTest.java` | 주석 정리(테스트 불변) |

## Class-by-Class Explanation

### Employee (modified)

- Package: `com.shinyoung.recruit.domain.entity`
- Type: Entity
- Change: `deptName` 일반 컬럼화. H2(ddl-auto)는 엔티티 선언으로 즉시 반영.

### EmployeeRepositoryTest (modified)

- Package: `com.shinyoung.recruit.domain.repository`
- Type: Test (@DataJpaTest)
- Added: 동일 `deptName`("IT센터") 임직원 2명 `saveAndFlush` 성공 검증 — 동일 부서 JIT 생성이 막히지 않음을 리포지토리 계층에서 실증.

## API

- 변경 없음.

## Entity Relationship Summary

- 구조 변경 없음 — `Employee.deptName` 컬럼 제약만 완화.

## Business Rules

1. 같은 부서 임직원 다수 허용 — deptName은 식별자가 아니라 LDAP 부서 속성이다.
2. JIT race 복구 semantics 불변: loginId race(재조회 = Employee)만 복구, 그 외 DB 제약 위반은 예외 전파.

## 운영(MariaDB) 수동 DDL

엔티티 선언이 이미 운영 스키마에 반영돼 있었다면 `employee.dept_name`에 Hibernate 생성명 unique 인덱스가 존재한다. 제거 절차:

```sql
-- 1) unique 제약/인덱스명 확인
SELECT CONSTRAINT_NAME
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'employee'
  AND CONSTRAINT_TYPE = 'UNIQUE';
-- 또는: SHOW INDEX FROM employee;

-- 2) 확인된 이름으로 제거
ALTER TABLE employee DROP INDEX <확인된_인덱스명>;
```

## Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*EmployeeRepositoryTest*" --tests "*RoutingAuthenticationProvider*" --tests "*UserRepositoryTest*" --no-daemon
```

## Test Results

- Result: **BUILD SUCCESSFUL — 10 tests / 0 failures**

| Test class | Tests | Result |
|------------|-------|--------|
| `EmployeeRepositoryTest` | 2 (동일 deptName 2명 저장 신규 포함) | passed |
| `RoutingAuthenticationProviderTest` | 4 | passed |
| `UserRepositoryTest` | 4 | passed |

- 전체 회귀 미실행(프로젝트 규칙 — 명시 요청 시에만). deptName unique에 의존하는 프로덕션 코드/테스트가 없음을 `deptName|dept_name` 전체 검색으로 확인.

## Known Limitations

1. 운영 MariaDB의 기존 unique 인덱스는 수동 DDL로 제거해야 한다(위 절차).
2. JIT race 복구의 "loginId race 외 제약 위반 전파" 경로는 이제 deptName 충돌로는 발생하지 않으나, NOT NULL 위반 등 다른 제약에 대해 여전히 유효하다.

## Next Phase Considerations

- Phase 09b 진행에 영향 없음.
- 운영 배포 시 `uk_users_login_id` 추가(05y)와 `employee.dept_name` unique 제거(본 fix)를 같은 DDL 작업 묶음으로 처리 권장.
