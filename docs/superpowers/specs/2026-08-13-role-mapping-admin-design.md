# 관리자 권한 관리 화면 + 면접관 권한 추가 — 설계 (2026-08-13, 승인됨)

## 목적

- 관리자 화면에서 **부서별 role 매핑**(`dept_role_mapping`)과 **사용자별 role 매핑**(신규 `user_role_mapping`)을 조회·추가·수정·삭제한다.
- 권한 축을 4개(운영 `ROLE_RECRUIT_ADMIN`, IT `ROLE_ADMIN`, 정보보호 `ROLE_PRIVACY_ADMIN`, 지원자 `ROLE_APPLICANT`)에서 **면접관 `ROLE_INTERVIEWER`** 를 더해 확장한다. `ROLE_INTERVIEWER`는 이미 `SecurityConfig:118`(`/api/interviewer/**`)에서 소비되지만 부여 경로가 없던 문자열을 공식화하는 것이다.
- 추후 면접관 페이지 개발의 선행 작업(권한 부여 경로 확보)이다.

## 확정된 결정 (사용자 승인)

| 결정 | 내용 |
|---|---|
| user 매핑 의미 | **추가 부여만(합집합)** — 최종 권한 = 부서 매핑 role ∪ 개인 매핑 role. revoke 없음 |
| 화면/API 접근 권한 | `ROLE_ADMIN` + `ROLE_RECRUIT_ADMIN` (기존 broad `/api/admin/**` 매처와 동일) |
| 삭제 기능 | 포함 (레포 관례대로 DELETE 동사 대신 `POST /{id}/delete`) |
| role 목록 단일 출처 | 백엔드 `RoleNames` 상수 클래스 + 목록 API (role 코드 테이블 신설 안 함) |
| user 지정 방식 | **loginId 문자열 직접 입력** (FK 없음) — 미로그인 직원(JIT 생성 전)에게도 사전 부여 가능. DB에 있으면 이름·부서 참고 표시 |

## 백엔드

### RoleNames (`security/RoleNames.java` — 신규)

- 상수 6종: `ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`, `ROLE_PRIVACY_ADMIN`, `ROLE_INTERVIEWER`, `ROLE_EMPLOYEE`, `ROLE_APPLICANT`.
- 매핑 화면에서 부여 가능한 role = 지원자 제외 5종 + 한국어 라벨.
- 기존 흩어진 리터럴 치환: `SecurityConfig`, `AdminAuditController:32`, `AdminClientEventLogController:36`, `CustomUserDetailsService:31`.

### UserRoleMapping (신규 엔티티, 테이블 `user_role_mapping`)

- `id`(PK, IDENTITY), `loginId`(varchar), `roleName`(varchar), BaseEntity 감사 필드.
- DB unique 제약 없음(기존 `dept_role_mapping` 관례), `(loginId, roleName)` 중복은 서비스 검증.
- 운영 DDL: `docs/codex/ops/` 관례대로 수동 SQL 추가 + `07-implementation-history.md` 기록.

### 로그인 병합 (`CustomLdapUserDetailsMapper`)

- 기존 부서 매핑 role에 `user_role_mapping`의 loginId 일치 role을 union + distinct.
- 임직원(LDAP) 경로만 해당. 지원자 경로(`ROLE_APPLICANT` 하드코딩)는 무변경.

### API (`AdminRoleMappingController` + `RoleMappingService`)

전부 `/admin/role-mappings/**`(실제 `/api/admin/role-mappings/**`) → 기존 broad 매처에 걸려 **SecurityConfig 무변경**. 인가는 테스트로 고정.

| 메서드·경로 | 용도 |
|---|---|
| GET `/admin/role-mappings/roles` | 부여 가능 role 목록 `{name, label}` |
| GET `/admin/role-mappings/dept` | 부서 매핑 전체 목록 |
| POST `/admin/role-mappings/dept` | 추가 `{deptName, roleName}` |
| POST `/admin/role-mappings/dept/{id}` | 수정(전체 교체) |
| POST `/admin/role-mappings/dept/{id}/delete` | 삭제 |
| GET `/admin/role-mappings/user` | 사용자 매핑 목록 (+`userName`/`userDeptName` nullable enrich) |
| POST `/admin/role-mappings/user` | 추가 `{loginId, roleName}` |
| POST `/admin/role-mappings/user/{id}` | 수정 |
| POST `/admin/role-mappings/user/{id}/delete` | 삭제 |

서버 검증: roleName은 부여 가능 5종만, 중복 매핑 거부, deptName trim 후 2자 이상(부분일치 오매칭 리스크 방어), loginId trim 후 비어있지 않음. 목록은 페이징 없음(소규모).

## 프론트

- 라우트 `/admin/role-mappings` (name `AdminRoleMapping`) — `adminRoutes` children, 부모 meta 상속. 가드/authStore 무변경.
- `src/views/admin/RoleMappingView.vue` — 탭 2개(부서별/사용자별), a-table + 추가/수정 모달. 전용 store 없음.
- `src/api/adminRoleMappingApi.ts`, `src/types/roleMapping.ts`.
- 사이드바 메뉴는 DB 관리 — 구현 후 메뉴 관리 화면에서 수동 등록(부트스트랩 관례).

## 범위 밖

- 면접관 페이지(`/api/interviewer/**` 화면) — 후속 슬라이스.
- 로그인 후 리다이렉트 분기(`LoginView` 주석 처리된 Employee 분기).
- 권한 revoke, dept 매핑 DB unique 제약, 사용자 검색 API.

## 검증

- 백엔드: 변경 클래스 지정 테스트(`RoleMapping*`, 매퍼, SecurityConfig 인가 401/403/통과).
- 프론트: `npm run type-check`.
- 계약: `api-contract.md` 🟡 → 구현 → 🟢.
