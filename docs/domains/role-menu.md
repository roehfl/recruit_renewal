# 권한·메뉴 (`role-menu`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [auth-account](auth-account.md)(로그인·세션·`RoleNames`·`SecurityConfig`), [privacy-audit](privacy-audit.md)(`ROLE_PRIVACY_ADMIN` 소비), [interview](interview.md)(`ROLE_INTERVIEWER` 소비), [board](board.md)(지원자 breadcrumb 사용 화면)

## 요약

- **메뉴**: DB `menu` 테이블의 2단계 트리(대메뉴→소메뉴). 사이트 `APPLICANT`(지원자 가로 헤더) / `ADMIN`(관리자 좌측 사이드바) 두 벌. 관리자 메뉴 관리 화면(`/admin/menus`)에서 생성·수정한다. 삭제 API 없음.
- **권한 매핑**: 관리자 권한 관리 화면(`/admin/role-mappings`)에서 부서별(`dept_role_mapping`)·사용자별(`user_role_mapping`) role 매핑을 CRUD한다. 매핑은 **임직원 LDAP 로그인 시점**에 권한(authority) 계산에 쓰인다(계산 코드는 [auth-account](auth-account.md) 소유).
- 메뉴와 권한은 서로 연결되지 않는다. 메뉴에는 role 컬럼이 없고 사이드바는 권한별 필터링을 하지 않는다. 접근 제어는 프론트 라우트 `meta.roles` + 백엔드 `SecurityConfig`가 한다.

## 용어

| 용어 | 뜻 |
|---|---|
| 대메뉴(메인메뉴) | `parent == null`인 루트 메뉴. `path` 없어도 됨(그룹 라벨) |
| 소메뉴(서브메뉴) | 대메뉴 하위 메뉴. `path` 필수. 소메뉴 아래에는 메뉴를 달 수 없음(최대 2단계) |
| `MenuSite` | `APPLICANT` · `ADMIN` — 메뉴가 속한 사이트 |
| `MenuType` | `ROUTE`(앱 내부 라우트, `/`로 시작) · `URL`(외부 링크, `http(s)://`, 새 창) |
| 그룹 메뉴 | `path`가 빈 대메뉴. 관리자 사이드바에서 작은 라벨로만 표시 |
| 부서 매핑 | `DeptRoleMapping(deptName, roleName)` — AD 그룹 cn **부분일치**로 매칭 |
| 사용자(개인) 매핑 | `UserRoleMapping(loginId, roleName)` — loginId **완전일치**. FK 없는 문자열 |
| 부여 가능 role | `RoleNames.ASSIGNABLE_ROLES` 5종: `ROLE_ADMIN`(IT 관리자), `ROLE_RECRUIT_ADMIN`(채용 운영 관리자), `ROLE_PRIVACY_ADMIN`(정보보호 관리자), `ROLE_INTERVIEWER`(면접관), `ROLE_EMPLOYEE`(일반 임직원). `ROLE_APPLICANT`는 제외 |
| authority 문자열 | `roleName`은 `ROLE_` 접두 포함 완전 문자열 → `hasAuthority`로 검사(`hasRole` 금지) |
| JIT 생성 | 임직원 `users`/`employee` 행은 최초 로그인 때 생성됨 → 로그인 전 직원에게도 사용자 매핑 가능 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/MenuController.java` | `/menu/**` 트리·단건·breadcrumb 조회, 메뉴 생성·수정 |
| controller | `{BE}/controller/AdminRoleMappingController.java` | `/admin/role-mappings/**` 부여 가능 role·부서/사용자 매핑 CRUD |
| service | `{BE}/service/MenuService.java` | 메뉴 검증(부모·2단계·path 규칙·중복), 2단 트리 조립, breadcrumb |
| service | `{BE}/service/RoleMappingService.java` | 매핑 검증(role 화이트리스트·부서명 2자·중복), 사용자 이름/부서 enrich |
| entity | `{BE}/domain/entity/Menu.java` | `site`·`type`·`parent`(self FK `parent_id`)·`name`·`path`·`sortOrder`·`icon`(varchar 100) |
| entity | `{BE}/domain/entity/DeptRoleMapping.java` | `deptName`·`roleName` (DB unique 없음) |
| entity | `{BE}/domain/entity/UserRoleMapping.java` | `loginId`·`roleName` (FK·unique 없음) |
| repository | `{BE}/domain/repository/MenuRepository.java` | site별 정렬 조회, `findBySiteAndPath`, 중복 검사 `existsBySiteAndPath[AndIdNot]` |
| repository | `{BE}/domain/repository/DeptRoleMappingRepository.java` | 목록·중복 검사, 로그인용 `findByDeptNameContainedIn`(부분일치 JPQL) |
| repository | `{BE}/domain/repository/UserRoleMappingRepository.java` | 목록·중복 검사, 로그인용 `findByLoginId` |
| dto | `{BE}/dto/request/MenuSaveRequest.java` | 메뉴 생성·수정 공용 요청 |
| dto | `{BE}/dto/request/DeptRoleMappingSaveRequest.java` | 부서 매핑 요청 |
| dto | `{BE}/dto/request/UserRoleMappingSaveRequest.java` | 사용자 매핑 요청 |
| dto | `{BE}/dto/response/MenuResponse.java` | 메뉴 노드(재귀 `children`) |
| dto | `{BE}/dto/response/MenuIdResponse.java` | `{ id }` |
| dto | `{BE}/dto/response/AssignableRoleResponse.java` | `{ name, label }` |
| dto | `{BE}/dto/response/DeptRoleMappingResponse.java` | 부서 매핑 행 |
| dto | `{BE}/dto/response/UserRoleMappingResponse.java` | 사용자 매핑 행 + `userName`/`userDeptName` |
| dto | `{BE}/dto/response/RoleMappingIdResponse.java` | `{ id }` |
| enum | `{BE}/enumeration/MenuSite.java` | `APPLICANT`, `ADMIN` |
| enum | `{BE}/enumeration/MenuType.java` | `ROUTE`, `URL` |
| exception | `{BE}/exception/InvalidMenuException.java` | 메뉴 제약 위반 400(현재 path 중복만) |
| exception | `{BE}/exception/InvalidRoleMappingException.java` | 매핑 검증 실패 400 |
| exception | `{BE}/exception/RoleMappingNotFoundException.java` | 매핑 id 없음 404 |
| test | `{BT}/controller/MenuControllerTest.java` | path 중복 400 + 한글 메시지(MockMvc, 보안 필터 없음) |
| test | `{BT}/service/MenuServiceTest.java` | icon 왕복, path 중복 규칙 6종 |
| test | `{BT}/service/RoleMappingServiceTest.java` | 부여 가능 목록, 매핑 CRUD·검증 |
| test | `{BT}/domain/repository/DeptRoleMappingRepositoryTest.java` | 부분일치 매칭(짧은 부서명 오매칭, 빈 부서명 제외) |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminMenuManage`(`/admin/menus`), `AdminRoleMapping`(`/admin/role-mappings`) — 부모 `meta.roles = ADMIN_ROLES` 상속(공유 파일) |
| view | `{FE}/views/admin/MenuManageView.vue` | 메뉴 관리. 사이트 탭 → 3단 컬럼(메인 › 서브 › 상세 폼), 아이콘 피커 |
| view | `{FE}/views/admin/RoleMappingView.vue` | 권한 관리. 탭(부서별/사용자별) a-table + 추가·수정 모달, 삭제 |
| component | `{FE}/layouts/AdminSidebar.vue` | `ADMIN` 트리 렌더(그룹 라벨 + 아이콘 소메뉴), 로그아웃 시 `clearMenuTree('ADMIN')`(공유 레이아웃) |
| component | `{FE}/layouts/ApplicantHeader.vue` | `APPLICANT` 트리 가로 헤더 렌더(아이콘 미사용)(공유 레이아웃) |
| component | `{FE}/common/antIcon.ts` | `ADMIN_MENU_ICONS`(명시 import 93종), `resolveAntIconOrFallback`(없으면 `AppstoreOutlined`) |
| api | `{FE}/api/menuApi.ts` | `getMenuTree`·`getBreadcrumb`·`createMenu`·`updateMenu` |
| api | `{FE}/api/adminRoleMappingApi.ts` | 권한 관리 9개 호출 |
| types | `{FE}/types/menu.ts` | `MenuSite`·`MenuType`·`MenuItem`·`MenuSaveRequest` |
| types | `{FE}/types/roleMapping.ts` | `AssignableRole`·`DeptRoleMapping`·`UserRoleMapping`·저장 요청 |
| store | `{FE}/stores/menuStore.ts` | site별 트리·breadcrumb 캐시, `isActiveMenu`(경로 trail 기반 활성 판정) |
| test | `{FE}/stores/__tests__/menuStore.spec.ts` | `isActiveMenu` 6케이스(그룹 대메뉴, URL 소메뉴, 상위 경로 fallback 등) |

## API 계약

모든 응답은 `ApiResponse<T>` = `{ success, data, message }`. 권한 열의 `ADMIN·RECRUIT_ADMIN`은 `hasAnyAuthority(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)`.

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /menu/tree | query `site`(기본 `APPLICANT`) | `MenuResponse[]` — 대메뉴 목록, 각 `children` 소메뉴 | 공개(명시 permitAll) |
| 🟢 | GET | /menu/{menuId} | path `menuId` | `MenuResponse`(`children: []`) | 공개(anyRequest) |
| 🟢 | GET | /menu/breadcrumb | query `site`(기본 `APPLICANT`), `path`(필수) | `MenuResponse[]` 루트→대상 순서, 각 `children: []` | 공개(anyRequest) |
| 🟢 | POST | /menu/admin/menu | `MenuSaveRequest` | `{ id }` | ADMIN·RECRUIT_ADMIN(명시 매처) |
| 🟢 | POST | /menu/admin/menu/{menuId} | `MenuSaveRequest`(전체 교체) | `{ id }` | ADMIN·RECRUIT_ADMIN(명시 매처) |
| 🟢 | GET | /admin/role-mappings/roles | 없음 | `[{ name, label }]` 부여 가능 5종 | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/role-mappings/dept | 없음 | `[{ id, deptName, roleName }]` id 오름차순, 페이징 없음 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/dept | `{ deptName, roleName }` | `{ id }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/dept/{id} | `{ deptName, roleName }`(전체 교체) | `{ id }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/dept/{id}/delete | 본문 없음 | `data: null` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /admin/role-mappings/user | 없음 | `[{ id, loginId, roleName, userName, userDeptName }]` id 오름차순 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/user | `{ loginId, roleName }` | `{ id }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/user/{id} | `{ loginId, roleName }`(전체 교체) | `{ id }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/role-mappings/user/{id}/delete | 본문 없음 | `data: null` | ADMIN·RECRUIT_ADMIN |

### 엔드포인트 상세

**메뉴 공통**
- 메뉴 노드(`MenuResponse` ↔ FE `MenuItem`): `{ id, parentId, site('APPLICANT'|'ADMIN'), type('ROUTE'|'URL'), name, path, sortOrder, icon, children:[...] }`. 트리는 최대 2단계.
- 저장 요청(`MenuSaveRequest` ↔ FE `MenuSaveRequest`): `{ site*, type*, parentId?, name*, path?, sortOrder?, icon? }`(`*` = `@NotNull`/`@NotBlank`). `parentId` null → 대메뉴, 값 있음 → 그 메뉴의 소메뉴. 생성·수정 같은 스키마.
- 매핑: FE `menuApi.getMenuTree()`/`getBreadcrumb()`/`createMenu()`/`updateMenu()` ↔ `MenuController.getMenuTree()`/`getBreadcrumb()`/`createMenu()`/`updateMenu()`.
- 🟢(2026-06-30) `icon` = ant-design-vue 아이콘 컴포넌트명 문자열 그대로(예: `"SettingOutlined"`). nullable, 백엔드 검증 없음(자유 문자열, `VARCHAR(100)`). 대메뉴·소메뉴 모두 허용. 지원자 메뉴는 null, 관리자 사이드바용. 🟢(2026-08-11) FE `MenuItem.icon: string | null` + 사이드바 아이콘 렌더링 — 백엔드 계약 변경 없음.
- **아이콘 허용 목록**: `icon`은 `{FE}/common/antIcon.ts`의 `ADMIN_MENU_ICONS`(93종)에서만 해석된다. 목록 밖이거나 null이면 `AppstoreOutlined`로 대체(오류 아님). DB에 넣는 값은 이 목록 안에 있어야 실제 아이콘이 보인다. 네임스페이스 import(`import * as`) 금지 — 790종 전체가 지원자 화면 공통 청크로 올라간다(측정: index 청크 +1,057kB / gzip +175kB). 아이콘 추가는 이 목록에 명시 import를 추가한다. 전체 목록 미리보기는 개발용 `{FE}/views/samples/MenuIconPreview.vue`(카드 없는 파일).
- `GET /menu/tree`: `findAllBySiteOrderBySortOrderAscIdAsc` 순서 그대로 대메뉴·소메뉴 정렬. `site` 값이 enum 밖이면 400 `"Invalid request."`.
- `GET /menu/{menuId}`: **FE 미사용**. 없는 id면 `IllegalArgumentException`(전용 핸들러 없음 → 500).
- `GET /menu/breadcrumb`: `site`+`path` **단건 조회**(`findBySiteAndPath`) 후 `parent`를 따라 올라가 루트부터 나열. 등록 안 된 path면 `IllegalArgumentException`(→ 500). FE 사용처는 지원자 `{FE}/views/applicant/ApplicantBreadcrumb.vue`(카드 없는 파일, `APPLICANT` 고정, 현재 `route.path`로 조회) 하나다.
- `POST /menu/admin/menu`: 메서드에 `@ResponseStatus(CREATED)`가 있지만 `ResponseEntity.ok`를 반환한다 — 성공 코드는 2xx로만 가정한다(테스트도 `is2xxSuccessful`).
- 🟢(2026-09-19) 경로 중복: 저장 시 **같은 site + 같은 path**를 쓰는 다른 메뉴가 있으면 400 `"같은 사이트에 동일한 경로를 사용하는 메뉴가 이미 있습니다. path={path}"`. 수정 시 자기 자신 제외, 빈 path(그룹 메뉴)는 검사 안 함, `ADMIN`·`APPLICANT` 간 같은 path 허용. 이미 중복된 기존 메뉴는 path를 고쳐야 저장된다.
- 🟢(2026-08-11) 보안: `MenuController` 기본 경로가 `/menu`라 실제 경로 `/api/menu/admin/menu`는 broad `/api/admin/**` 매처에 안 걸린다. `SecurityConfig`에 `POST /api/menu/admin/menu`, `POST /api/menu/admin/menu/*` → `hasAnyAuthority(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)` 명시 매처로 막는다(`{BT}/config/SecurityConfigTest.java` 메뉴 6건: 비인증 401 / 타권한 403 / 관리자 통과 / tree permitAll 회귀). 경로 자체는 계약 안정성을 위해 유지한다.
- 범위 밖(미구현): 메뉴 삭제(만들면 DELETE 대신 POST), 메뉴 뱃지, "사용 여부" 토글(엔티티·응답에 필드 없음 — 필요하면 엔티티·DDL·트리 필터링과 함께 별도 슬라이스).

**관리자 메뉴 관리 화면(MenuManageView) — 🟢, 백엔드 계약 변경 없음**
- 탭(`APPLICANT`/`ADMIN`) → 3단 컬럼. 한 번에 메인 또는 서브 **하나만** 편집. 서브 추가는 **저장된 메인메뉴가 선택된 경우에만**(아니면 `+` 비활성).
- 폼 → 요청: 메뉴명→`name`, 상위 메인메뉴(읽기 전용)→`parentId`, 유형→`type`, 경로→`path`(trim, 빈 값은 null), 정렬 순서→`sortOrder`, 아이콘→`icon`. `site`는 활성 탭.
- 아이콘 피커는 **`ADMIN` 탭 서브메뉴에서만** 노출(화면 레벨 제약). 피커가 숨은 경우에도 폼의 `icon` 값을 그대로 보낸다(null로 덮으면 저장된 아이콘이 지워짐).
- 클라이언트 검증은 서버 검증 미러링(메뉴명 필수, 소메뉴 path 필수, `ROUTE`는 `/`, `URL`은 `http(s)://`, 같은 탭 트리 안 path 중복). 서버가 단일 출처.
- 부트스트랩: 메뉴는 DB에만 있다. 사이드바에 "메뉴 관리"가 없으면 `/admin/menus`로 직접 접속해 대메뉴 + 소메뉴 "메뉴 관리"를 등록한다.

**관리자 사이드바 운용 규약(`GET /menu/tree?site=ADMIN`)**
- 대메뉴 = path 없는 그룹 라벨(이동 안 함), 소메뉴 = 아이콘 + 이름의 이동 대상. 하위가 없는 대메뉴는 그 자체를 이동 항목으로 표시.
- 활성 표시는 `menuStore.isActiveMenu(site, menu, currentPath)`(경로 trail 기반): `ROUTE` 메뉴만 비교, 완전일치 우선, 없으면 가장 긴 상위 경로 메뉴. 메뉴에 없는 상세 화면은 route `meta.activeMenuPath`로 지정.
- `URL` 메뉴는 `window.open(path, '_blank', 'noopener,noreferrer')`, `ROUTE`는 `router.push(path)`(헤더·사이드바 동일).

**권한 관리(RoleMappingView) — 🟢(2026-08-13)**
- `GET /roles`: 단일 출처는 백엔드 `RoleNames`. FE는 role 목록을 하드코딩하지 않고 이 응답으로 선택지·라벨을 만든다.
- 부서 매핑 저장: `roleName`은 부여 가능 5종만, `deptName` trim 후 **2자 이상**, `(deptName, roleName)` 중복 거부. 같은 부서에 다른 role은 여러 개 가능.
- 사용자 매핑 저장: `loginId` trim 후 필수, `roleName` 5종, `(loginId, roleName)` 중복 거부. 목록의 `userName`/`userDeptName`은 `users`에 그 loginId가 있을 때만 채움(`userDeptName`은 `Employee`일 때만), 없으면 null → 화면 "미등록".
- 없는 id 수정·삭제 → 404. 검증 실패 → 400(`message`에 한글 사유). 수정·삭제도 POST(DELETE 동사 미사용).
- 인가: 전부 `/api/admin/**` broad 매처(ADMIN·RECRUIT_ADMIN) — `SecurityConfig` 전용 매처 없음. `SecurityConfigTest` 권한관리 6건(비인증 401, 지원자·면접관 403, IT·운영 관리자 통과).

## 규칙·불변식

**메뉴**
- 부모가 지정되면 존재해야 한다 — 없으면 `IllegalArgumentException`. ({BE}/service/MenuService.java — findParent)
- 부모와 자식의 `site`가 같아야 한다. ({BE}/service/MenuService.java — validateParentSite)
- 최대 2단계: 부모는 루트여야 한다(소메뉴 아래 등록 불가). ({BE}/service/MenuService.java — validateTwoLevelMenu)
- 수정 시 자기 자신을 부모로 지정할 수 없다. ({BE}/service/MenuService.java — validateSelfParent)
- 소메뉴는 `path` 필수, 대메뉴는 path 생략 가능. path가 있으면 `ROUTE`는 `/`로, `URL`은 `http://`·`https://`로 시작. ({BE}/service/MenuService.java — validatePathRule)
- 같은 site 안 path 유일(빈 path 제외, 수정 시 자기 제외, 사이트 간 허용) → `InvalidMenuException` 400. 이유: breadcrumb이 `site`+`path` 단건 조회라 중복이면 `findBySiteAndPath`가 깨진다. ({BE}/service/MenuService.java — validateDuplicatedPath)
- 위 규칙 중 **path 중복만 400**이다. 나머지(`IllegalArgumentException`)는 `GlobalExceptionHandler`에 핸들러가 없어 500으로 나간다 — 화면 검증이 먼저 거르는 이유. ({BE}/service/MenuService.java — create/update)
- 트리 조립은 루트와 그 직계 자식만 담는다(3단계 데이터가 있어도 손자는 응답에서 빠짐). ({BE}/service/MenuService.java — buildTwoLevelTree)
- `sortOrder`: 생성 시 null이면 null 저장, 수정 시 null이면 0. ({BE}/domain/entity/Menu.java — update)
- 메뉴 쓰기(POST `/menu/admin/menu`, `/menu/admin/menu/*`)는 ADMIN·RECRUIT_ADMIN만, 조회 3종은 공개. (`{BE}/config/SecurityConfig.java` — [auth-account](auth-account.md) 소유)

**권한 매핑**
- `roleName`은 trim 후 `RoleNames.isAssignable`이어야 한다(5종, `ROLE_APPLICANT` 불가). ({BE}/service/RoleMappingService.java — validateAssignableRole)
- `deptName`은 trim 후 2자 이상(`MIN_DEPT_NAME_LENGTH`). 짧은 부서명은 로그인 부분일치에서 다른 부서 그룹에 오매칭된다(예: "채널" → "내부채널_부서_6315"). ({BE}/service/RoleMappingService.java — normalizeDeptName)
- `loginId`는 trim 후 비어 있으면 안 된다. ({BE}/service/RoleMappingService.java — normalizeLoginId)
- `(deptName, roleName)`·`(loginId, roleName)` 중복은 **서비스에서만** 막는다(DB unique 없음, 수정 시 자기 제외). ({BE}/service/RoleMappingService.java — createDeptMapping/updateDeptMapping/createUserMapping/updateUserMapping)
- 없는 매핑 id → `RoleMappingNotFoundException` 404. ({BE}/service/RoleMappingService.java — findDeptMapping/findUserMapping)
- 수정은 전체 교체(`deptName`/`loginId`와 `roleName` 모두 덮어씀). ({BE}/domain/entity/DeptRoleMapping.java — update, {BE}/domain/entity/UserRoleMapping.java — update)

**매핑이 역할 부여에 쓰이는 방식(로그인 — [auth-account](auth-account.md) 소유 코드)**
- 임직원 LDAP 로그인 때만 계산한다. 지원자는 `ROLE_APPLICANT` 하드코딩(매핑 무관).
- 부서 role: LDAP이 찾은 그룹 cn(예: `내부채널_부서_6315`)마다 `groupName LIKE %deptName%`인 부서 매핑을 모은다(빈 `deptName` 제외). ({BE}/domain/repository/DeptRoleMappingRepository.java — findByDeptNameContainedIn)
- 개인 role: `sAMAccountName`(loginId) 완전일치 사용자 매핑. ({BE}/domain/repository/UserRoleMappingRepository.java — findByLoginId)
- 최종 authority = 부서 role ∪ 개인 role, distinct. **추가 부여만, revoke 없음**. 표시 부서명은 첫 매칭 부서 매핑의 `deptName` → 없으면 `department` 속성 → 첫 그룹 cn. (`{BE}/security/auth/CustomLdapUserDetailsMapper.java` — mapUserFromContext)
- 권한은 로그인 시점에 세션에 담긴다. 매핑을 바꿔도 이미 로그인한 세션에는 재로그인 전까지 반영되지 않는다.
- 매핑이 하나도 안 걸린 임직원은 authority가 비어 관리자 화면(`ADMIN_ROLES` = `ROLE_ADMIN`·`ROLE_RECRUIT_ADMIN`)에 못 들어간다. `ROLE_PRIVACY_ADMIN`만 가진 사용자도 `/admin` 라우트 가드에 막힌다(`{FE}/routes/adminRoutes.ts` — `ADMIN_ROLES`).

## 변경 레시피

### 새 관리자·지원자 화면을 메뉴에 노출
1. 라우트를 추가한다(관리자는 `{FE}/routes/adminRoutes.ts` children — 권한은 부모 meta 상속). 코드로 메뉴를 넣지 않는다.
2. 배포 후 `/admin/menus`에서 해당 탭에 소메뉴(`ROUTE`, path = 라우트 경로, 관리자면 아이콘)를 등록한다. 같은 site에 같은 path가 있으면 400.
3. 지원자 화면에 `ApplicantBreadcrumb`를 쓰면 그 경로가 `APPLICANT` 메뉴에 반드시 등록돼 있어야 한다(없으면 breadcrumb 500).
4. 메뉴에 없는 상세 화면은 route `meta.activeMenuPath`로 활성 메뉴를 지정한다.

### 메뉴 필드·검증 변경
1. `{BE}/domain/entity/Menu.java`(`create`/`update` 시그니처) → `{BE}/dto/request/MenuSaveRequest.java` → `{BE}/dto/response/MenuResponse.java` → `{BE}/service/MenuService.java`.
2. 400으로 내려야 하는 검증은 `InvalidMenuException`을 쓴다(`IllegalArgumentException`은 500).
3. 운영 DB는 ddl-auto로 컬럼이 안 생길 수 있다 — 수동 SQL을 `recruit_back/recruit_backend/docs/ops/`에 추가한다.
4. `{BT}/service/MenuServiceTest.java`(+ 400 응답이면 `{BT}/controller/MenuControllerTest.java`)에 테스트 추가 → 백엔드 검증 실행.
5. `{FE}/types/menu.ts` → `{FE}/views/admin/MenuManageView.vue`(폼·`validateForm` 미러) → 필요하면 `{FE}/layouts/AdminSidebar.vue`·`{FE}/stores/menuStore.ts`.
6. `npm run type-check`, 카드 `## API 계약`·`## 규칙·불변식` 갱신, `node tools/check-docs.mjs`.

### 메뉴 쓰기 API 추가(예: 삭제)
1. 레포 관례대로 POST(예: `/menu/admin/menu/{menuId}/delete`)로 만든다.
2. **`SecurityConfig` 매처를 반드시 추가한다.** 기존 `/api/menu/admin/menu/*`는 한 세그먼트만 매칭해 `/{id}/delete`를 못 막는다 → 안 넣으면 `anyRequest().permitAll()`로 비인증 호출 가능. `{BT}/config/SecurityConfigTest.java`에 401/403/통과 케이스 추가.
3. 삭제라면 자식 메뉴 처리(자식 있는 대메뉴 삭제 거부 등)를 정해 서비스에 넣고 테스트한다.
4. `{FE}/api/menuApi.ts` → `{FE}/views/admin/MenuManageView.vue`, 카드 API 표에 🟡로 먼저 적고 구현 후 🟢, `node tools/check-docs.mjs`.

### 부여 가능 role 추가·매핑 검증 변경
1. role 상수·라벨은 `{BE}/security/auth/RoleNames.java`(`ASSIGNABLE_ROLES`)에 추가 — [auth-account](auth-account.md) 카드도 갱신.
2. 새 role로 막을 경로가 있으면 `{BE}/config/SecurityConfig.java` 매처를 broad `/api/admin/**`보다 **먼저** 추가(순서가 보안 요구사항).
3. 검증 변경은 `{BE}/service/RoleMappingService.java` + `{BT}/service/RoleMappingServiceTest.java`. 부분일치 규칙을 건드리면 `{BT}/domain/repository/DeptRoleMappingRepositoryTest.java`도.
4. FE는 role 목록을 API로 받으므로 보통 변경 없음. 관리자 화면 진입 role이 바뀌면 `{FE}/routes/adminRoutes.ts`의 `ADMIN_ROLES`. 화면 검증(`validateDeptForm`/`validateUserForm`) 미러.
5. 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.MenuControllerTest" --tests "com.shinyoung.recruit.service.MenuServiceTest" --tests "com.shinyoung.recruit.service.RoleMappingServiceTest" --tests "com.shinyoung.recruit.domain.repository.DeptRoleMappingRepositoryTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.MenuControllerTest" --tests "com.shinyoung.recruit.service.MenuServiceTest" --tests "com.shinyoung.recruit.service.RoleMappingServiceTest" --tests "com.shinyoung.recruit.domain.repository.DeptRoleMappingRepositoryTest" --no-daemon
```

인가·로그인 권한 계산을 건드렸으면 [auth-account](auth-account.md) 소유 테스트도 함께 돌린다: `--tests "com.shinyoung.recruit.config.SecurityConfigTest" --tests "com.shinyoung.recruit.security.auth.CustomLdapUserDetailsMapperTest"`.

프론트(`recruit_front/`에서):

```bash
npm run type-check
npx vitest run src/stores/__tests__/menuStore.spec.ts
```

## 함정·결정

- `523ab48` 메뉴 path 중복 검증 **복원**(주석 처리돼 꺼져 있던 `validateDuplicatedPath` 재활성). 다시 끄지 않는다 — 끄면 breadcrumb 단건 조회가 깨진다.
- `8d7485d` 서버 검증이 꺼져 있던 시기에 화면 측 중복 path 차단을 추가했다. `MenuManageView.vue` `findDuplicatePathMenu` 주석의 "서버 중복 검증이 꺼져 있어"는 현재 사실과 다르다(서버도 검증함).
- `6e7f6cc` 메뉴 쓰기 엔드포인트 인가 게이팅. 컨트롤러 경로가 `/menu/admin/...`라 `/api/admin/**` 매처 밖이다 — 새 쓰기 경로를 만들 때마다 명시 매처 필요(위 레시피).
- `524d9d6` 권한 관리 API + 사용자 매핑 + `ROLE_INTERVIEWER` 부여 경로 신설. 결정: 사용자 매핑은 합집합 추가만(revoke 없음), loginId는 FK 없는 문자열, role 목록 단일 출처는 `RoleNames`(코드 테이블 없음), 목록 페이징 없음(소규모 전제).
- 조회 3종(`/menu/tree`, `/menu/{menuId}`, `/menu/breadcrumb`)은 비로그인 공개다. `site=ADMIN` 트리도 공개로 보인다(현 결정: tree permitAll 유지).
- 수정 API는 "자식이 있는 대메뉴를 다른 대메뉴 밑으로 옮기기"·"자식 있는 대메뉴의 site 변경"을 막지 않는다. 화면은 이런 이동을 만들지 않지만 API 직접 호출 시 트리에서 손자가 사라지거나 site가 섞인다.
- 서버는 `path`를 trim하지 않는다(화면이 trim해서 보냄). `"/a "`와 `"/a"`는 서버에서 다른 path다.
- `recruit_back/recruit_backend/docs/adr/0007-privacy-admin-role-separation.md` — 비가역 파기·민감 감사는 `ROLE_PRIVACY_ADMIN`으로 분리(`ROLE_RECRUIT_ADMIN` 재사용 금지), 두 권한 모두 부서 매핑에서 파생. 주의: 권한 관리 API는 `ROLE_RECRUIT_ADMIN`에게도 열려 있어 운영 관리자가 `ROLE_PRIVACY_ADMIN` 매핑을 만들 수 있다(설계 시 ADMIN·RECRUIT_ADMIN 접근으로 승인됨). 직무 분리를 강화하려면 `/api/admin/role-mappings/**` 쓰기 전용 매처를 broad 매처보다 먼저 추가한다.
- 수동 DDL(ddl-auto를 validate/none으로 쓰는 운영 DB): `recruit_back/recruit_backend/docs/ops/role-mapping-user-role-mapping-ddl.sql`(`user_role_mapping` 테이블 + `login_id` 인덱스), `recruit_back/recruit_backend/docs/ops/fix-employee-dept-name-unique-drop.sql`(`employee.dept_name`에 남은 unique 인덱스 제거 — 같은 부서 두 번째 임직원 JIT 생성 실패 → 로그인 불가 결함). `menu`·`dept_role_mapping`용 DDL 파일은 없다.
