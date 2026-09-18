# 관리자 공통코드(CommonCode) 관리 화면 설계

- 작성일: 2026-08-28
- 대상: 관리자 화면 `/admin/codes` — 공통코드 등록 / 수정 / 삭제(비활성화)
- 범위: **프론트 단독**. 백엔드 코드·스키마 변경 없음.

## 1. 배경 및 현황

CommonCode는 Phase 08a에서 도입된 런타임 관리 lookup master다. 백엔드 CRUD API는 이미 존재하지만
관리자 화면이 없어 코드 등록 경로가 없다. 시드 마이그레이션도 없으므로 **이 화면이 유일한 등록 경로**가 된다.

### 1.1 기존 백엔드 (변경 없이 그대로 사용)

| 엔드포인트 | 용도 |
| --- | --- |
| `GET /api/codes?groupCode=` | public. 활성 코드만 `sortOrder` 순. 지원자 화면 드롭다운 소스 |
| `GET /api/admin/codes?groupCode=` | admin. 비활성 포함. `groupCode` 생략 시 전체(그룹/정렬 순) |
| `POST /api/admin/codes` | 생성 |
| `POST /api/admin/codes/{id}` | 수정 (soft delete 포함) |

- 엔티티 필드: `id / groupCode / code / displayName / sortOrder / active / description(500자)`
- `groupCode`, `code`는 **생성 후 불변**. 삭제는 `active=false` soft delete만 존재(하드 삭제 API 없음).
- 중복(`groupCode`+`code`)은 `InvalidCommonCodeException` → 400.
- 권한: `SecurityConfig`의 `/api/admin/**` → `ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`.

### 1.2 현재 코드 소비처 (화면이 커버해야 할 전부)

| groupCode | 소비 위치 | 백엔드 검증 결합 |
| --- | --- | --- |
| `NATIONALITY` | 지원서 > 기본정보 | O (`existsByGroupCodeAndCodeAndActiveTrue`) |
| `DISABILITY_TYPE` | 지원서 > 기본정보 | O |
| `DISABILITY_GRADE` | 지원서 > 기본정보 | O |
| `MAJOR_TYPE` | 지원서 > 학력 | X |
| `LANGUAGE_TYPE` | 지원서 > 어학 | X |
| `LANGUAGE_CONVERSATION` | 지원서 > 어학 | X |
| `LANGUAGE_TEST_{languageCode}` (동적) | 지원서 > 어학 시험명 | X |
| `LANGUAGE_LEVEL` | 관리자 > 지원현황 검색 필터 | X |

`LANGUAGE_TEST_*`처럼 그룹 이름이 런타임에 파생되므로, 그룹 선택 UI를 고정 목록으로 못 박으면 안 된다.

## 2. 결정사항

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 삭제 의미 | soft delete만 (`active=false`) | 백엔드에 하드 삭제 없음. 기존 지원서의 코드 참조 보존, 복구 가능 |
| 그룹 관리 | `CODE_GROUP` 자기참조 그룹 | 백엔드 변경 0. 그룹 한글명을 런타임 관리. 동적 그룹도 그대로 등록 가능 |
| 화면 구조 | 단일 테이블 + 그룹 필터 | 그룹/코드가 같은 테이블 스키마라 한 테이블로 충분 |
| 비활성화 안전장치 | 확인 모달 + 경고 문구 | 참조 카운트 API는 동적 그룹 커버 불가 대비 비용 과다 |
| 사용 화면 표시 | `CODE_GROUP` 행의 `description`에 수기 기재 | 필드 이미 존재. 프론트 상수 맵/백엔드 컬럼 추가 없음 |
| 정렬순서 기본값 | 그룹 내 최대값 + 10 | 중간 삽입 여지 |

## 3. 동작 모델

### 3.1 그룹 self-host

그룹 자체를 `groupCode = 'CODE_GROUP'`인 코드 행으로 등록한다.

| groupCode | code | displayName | description |
| --- | --- | --- | --- |
| `CODE_GROUP` | `NATIONALITY` | 국적 | 지원서 > 기본정보 국적 셀렉트 · 백엔드 검증 결합 |
| `CODE_GROUP` | `LANGUAGE_TEST_ENGLISH` | 영어 시험명 | 지원서 > 어학 시험명 (LANGUAGE_TYPE=ENGLISH 하위) |
| `NATIONALITY` | `KR` | 대한민국 | (선택) |

- 그룹 행의 `description`이 곧 **사용 화면 메모**다. 관리자 수기 입력이며 코드와 자동 동기화되지 않는다(한계 인지).
- `CODE_GROUP`도 그룹 셀렉트에 나타나므로, 그룹 등록/수정/비활성화를 같은 테이블에서 처리한다. 별도 화면 없음.

### 3.2 데이터 로딩

- 화면 진입 시 `GET /api/admin/codes` (groupCode 없이) **1회** 호출 → 전체 코드를 메모리에 보관.
- 그룹 셀렉트 = `CODE_GROUP` 그룹의 코드 목록 ∪ 응답에 실제 존재하는 distinct `groupCode`.
- 후자에만 있는 그룹은 `(미등록)` 표시 → 그룹 등록 유도.
- 그룹 전환·키워드 검색·비활성 필터는 모두 **클라이언트 측 처리**(재요청 없음).
- 생성/수정 성공 시 전체를 다시 로드해 그룹 목록과 테이블을 함께 갱신한다.

## 4. 화면 사양

라우트: `/admin/codes`, name `AdminCommonCodeManage`, `adminRoutes` children.
인증·권한은 부모 `/admin`의 `meta`(`ROLE_ADMIN`, `ROLE_RECRUIT_ADMIN`)를 상속한다.

### 4.1 툴바

- 그룹 셀렉트(`a-select`, `show-search`) — 표기 `표시명 · GROUP_CODE`, 미등록 그룹은 `GROUP_CODE (미등록)`
- 키워드 입력 — `code` / `displayName` 부분일치, 클라이언트 필터
- `비활성 포함` 체크박스 — 기본 ON
- `코드 등록` 버튼 — 현재 선택 그룹을 `groupCode`로 프리필

### 4.2 안내 영역

| 조건 | 표시 |
| --- | --- |
| 그룹이 `CODE_GROUP`에 등록됨 + `description` 있음 | `사용 화면` 칩 + 메모 + 메모 수정 버튼 |
| 등록됨 + `description` 없음 | `사용 화면 미기재` + `메모 작성` 버튼 |
| `CODE_GROUP` 미등록 그룹 | 경고 배너 + `그룹 등록` 버튼(code 프리필) |
| 선택 그룹이 `CODE_GROUP` | 안내 배너(설명란이 곧 사용 화면 메모) |
| 선택 그룹이 백엔드 검증 결합 그룹 | 경고 배너(비활성화 시 지원자 저장 실패) |

검증 결합 그룹 상수: `['NATIONALITY', 'DISABILITY_TYPE', 'DISABILITY_GRADE']` — 백엔드 `ApplicationBasicInfoService`와 일치.

### 4.3 테이블 (`a-table`)

컬럼: 정렬순서 / 코드 / 표시명 / 설명 / 상태 / 액션
- 선택 그룹이 `CODE_GROUP`이면 설명 컬럼 헤더를 `설명 · 사용 화면`으로 바꾼다.
- 비활성 행은 흐리게 표시하고 `비활성` 태그를 단다.
- 액션: `수정`, `비활성화`(활성 행) 또는 `활성화`(비활성 행).
- 빈 목록이면 등록 유도 문구.

### 4.4 등록·수정 모달 (`a-modal`)

| 필드 | 등록 | 수정 |
| --- | --- | --- |
| `groupCode` | 입력 가능(현재 그룹 프리필) | disabled |
| `code` | 입력 가능 | disabled |
| `displayName` | 필수 | 필수 |
| `sortOrder` | 그룹 내 최대값 + 10 | 수정 가능 |
| `description` | 선택 | 선택 |
| `active` | 기본 체크 | 수정 가능 |

- 수정 모드에 "그룹코드와 코드는 생성 후 변경할 수 없습니다." 힌트 노출.
- `CODE_GROUP` 대상이면 설명 필드 라벨을 `설명 · 사용 화면`으로 하고 예시 힌트를 보여준다.
- 클라이언트 검증: `groupCode`/`code`/`displayName` 필수, 길이(100/100/200/500) 체크.
- 중복 코드 등 서버 오류는 `getApiErrorMessage`로 백엔드 메시지를 그대로 표시한다.

### 4.5 비활성화 확인 모달

- 대상 표기(`GROUP / CODE — 표시명`) + 공통 경고: 지원자 선택지에서 즉시 사라짐, 기존 저장 데이터는 값이 남지만 표시명이 비어 보일 수 있음, 재활성화로 복구 가능.
- 검증 결합 그룹이면 추가 경고: 해당 값을 가진 지원자의 지원서 저장이 실패함.
- 대상이 `CODE_GROUP` 행이면 추가 안내: 하위 코드는 계속 동작하며 이 화면 그룹 목록에서만 가려짐.
- 활성화(복구)는 확인 없이 즉시 실행.

## 5. API 계약

신규 엔드포인트 없음. 프론트가 기존 관리자 API 3개를 그대로 호출한다.
`api-contract.md`에 관리자 CommonCode 섹션이 없으므로 신설하고, 구현과 일치시켜 🟢로 확정한다.

```
GET  /api/admin/codes?groupCode=            → ApiResponse<CommonCodeItems[]>
POST /api/admin/codes                       → ApiResponse<CommonCodeItems>
POST /api/admin/codes/{id}                  → ApiResponse<CommonCodeItems>
```

요청 필드 모양(백엔드 DTO가 단일 출처):
- 생성: `groupCode`, `code`, `displayName`, `sortOrder?`, `active?`, `description?`
- 수정: `displayName`, `sortOrder?`, `active?`, `description?`

## 6. 파일 변경

신규
- `recruit_front/src/api/adminCommonCodeApi.ts`
- `recruit_front/src/views/admin/AdminCommonCodeManageView.vue`

수정
- `recruit_front/src/types/commonCode.ts` — `CommonCodeCreateRequest`, `CommonCodeUpdateRequest` 추가
- `recruit_front/src/routes/adminRoutes.ts` — `codes` 라우트 추가
- `recruit/api-contract.md` — 관리자 CommonCode 섹션 신설

## 7. 검증

- `npm run type-check` (필요 시 `npm run build`)
- 백엔드 무변경이므로 백엔드 테스트 불필요
- 수동 확인: `CODE_GROUP` 그룹 등록 → 해당 그룹에 코드 등록 → 지원서 화면 드롭다운 반영 → 코드 비활성화 후 드롭다운에서 사라짐 → 재활성화 복구

## 8. 범위 밖

하드 삭제 API, 참조 건수 조회 API, 드래그 정렬, 별도 그룹 테이블/컬럼 신설, 사용 화면 프론트 상수 맵,
`LANGUAGE_TYPE` → 하위 시험명 그룹 점프 링크, 지원자 화면(`BasicInfoSection` 등) 수정, 백엔드 코드 변경.

## 9. 알려진 한계

- 사용 화면 메모는 관리자 수기 입력이라 실제 코드와 어긋날 수 있다. 그룹 추가 시 메모를 함께 적는 운영 규칙에 의존한다.
- 참조 검사가 없으므로 비활성화의 실제 영향 범위는 경고 문구로만 알린다.
- `CODE_GROUP` 미등록 그룹은 한글명 없이 코드로만 노출된다.
