# 로그 조회 화면 설계 (2026-09-20)

관리자 콘솔에 **로그 조회** 화면 1개를 추가한다. 탭 2개로 `지원자 이벤트`(브라우저 오류)와 `감사 로그`(서버 행위 증적)를 조회한다.

- 목업: `design/로그-조회.html` (샘플 데이터로 동작하는 자립형 HTML)
- 관련 카드: [client-event-log](../../domains/client-event-log.md) · [privacy-audit-audit](../../domains/privacy-audit-audit.md) · [admin-application](../../domains/admin-application.md)(지원자 찾기 API 소유) · [role-menu](../../domains/role-menu.md)(사이드바 메뉴)

## 1. 배경·목표

두 로그 모두 백엔드 조회 API가 이미 있고(🟢) **프론트 화면만 없다**. 운영자는 지금 DB를 직접 보거나 API를 직접 호출해야 한다.

주 용도는 **CS·사고 추적**이다. 지원자 문의가 들어왔을 때 그 지원자의 브라우저에서 무슨 오류가 났는지 확인하는 것이 1순위고, 관리자 행위 증적 조회는 별개 용도로 같은 화면에 둔다.

성공 기준:
- 이름·휴대폰만 아는 상태에서 해당 지원자의 브라우저 오류 이벤트에 도달할 수 있다.
- 한 이벤트의 전체 필드(스택 요약·metadata 포함)를 화면에서 볼 수 있다.
- 감사 로그를 행위·결과·행위자·지원번호·공고로 좁혀 조회할 수 있다.
- 백엔드 코드 변경 0건.

## 2. 범위

### 하는 것
- 관리자 라우트 `/admin/logs` 신설, 탭 2개(`지원자 이벤트` 기본 / `감사 로그`).
- 지원자 이벤트 탭: 지원자 찾기 → 대상 지정 → 목록 → 상세 drawer.
- 감사 로그 탭: 필터 → 목록 → 상세 drawer.
- 두 탭 사이 크로스 점프(지원번호 기준).
- 권한별 민감 필드 마스킹 표시.

### 안 하는 것 (명시적 기각)
| 항목 | 기각 사유 |
|---|---|
| 감사 로그 검색에 `correlationId` 필터 추가 | 백엔드 변경이 필요하다. 클라 이벤트 → 감사 로그 역추적 한 단계가 끊기지만 `applicationId` 축으로 대체한다. 사용자 결정(2026-09-20). |
| 로그 검색 API에 `name`·`phoneNumber` 추가 | 감사 로그는 설계상 PII-free다. 파기된 지원자는 이름 스냅샷이 익명화돼 조인도 실패한다. 감사 로그는 영구 보존이라 이름으로 못 찾는 행이 계속 늘어난다. |
| `POST /admin/client-events/cleanup` 버튼 | 매일 04:00 스케줄러가 같은 일을 한다. 감사 로그도 남지 않는 파괴 동작이라 화면에 두지 않는다. |
| 로그 엑셀 반출 | 요청에 없다. 반출은 그 자체로 감사 대상이라 별도 설계가 필요하다. |
| 지원현황 화면에 "로그 보기" 링크 추가 | 딥링크 규격(`?tab=&applicationId=`)은 이 설계에 포함하되, 링크를 다는 것은 [admin-application](../../domains/admin-application.md) 화면 변경이라 별도 슬라이스로 둔다. |

## 3. 사용자·권한

| 권한 | 화면 진입 | 조회 | 민감 필드 |
|---|---|---|---|
| `ROLE_RECRUIT_ADMIN` | 가능 | 가능 | `***` 마스킹 |
| `ROLE_PRIVACY_ADMIN` | **불가** (아래 함정) | — | — |
| `ROLE_ADMIN`만 | 가능 | **403** | — |

두 가지 기존 함정을 화면에서 처리한다.

1. **`ROLE_ADMIN`만 가진 계정**: 라우트 가드 `ADMIN_ROLES = ['ROLE_ADMIN','ROLE_RECRUIT_ADMIN']`는 통과하지만 두 조회 API는 `ROLE_RECRUIT_ADMIN`·`ROLE_PRIVACY_ADMIN`만 허용한다. 조회를 쏘면 공통 인터셉터가 403을 받아 `/403`으로 튕긴다. → **탭 본문을 렌더링하지 않고 안내 `a-alert`만 표시한다.** ([AdminRetentionView.vue](../../../recruit_front/src/views/admin/retention/AdminRetentionView.vue)와 같은 방식)
2. **`ROLE_PRIVACY_ADMIN`만 가진 계정**: `ADMIN_ROLES`에 없어 `/admin` 자체에 못 들어온다. 정작 IP·UA 원문을 볼 수 있는 사람이 화면에 도달하지 못한다. → **이 설계 범위 밖**. 고치려면 `ADMIN_ROLES` 또는 이 라우트의 `meta.roles`를 바꿔야 하는데, `ADMIN_ROLES`는 로그인 직후 이동 분기와 공유하는 값이라 영향 범위가 넓다. 별도 결정 필요 항목으로 남긴다(§10).

민감 필드는 백엔드가 이미 마스킹해서 보낸다. 프론트는 판정하지 않고 받은 값을 그대로 표시한다.
- 감사 로그: `ipAddress`·`userAgent`
- 지원자 이벤트: `ipAddress`·`userAgent`·`principalHash`·`principalType`

`***`가 보이는 사용자에게는 화면 상단에 "원문 확인에는 `ROLE_PRIVACY_ADMIN` 권한이 필요합니다" 안내 배너를 띄운다. 배너 노출 판정은 `authStore.roles`로 한다.

> 목업의 `개인정보 관리자 권한으로 보기` 체크박스는 **데모용**이다. 구현할 때 제거한다.

## 4. 화면 구조

```
/admin/logs?tab=client|audit
└ AdminLogView.vue                     페이지 헤더 · 마스킹 안내 · 권한 가드 · 탭 껍데기
  ├ [탭1] ApplicantEventPanel.vue      기본 탭
  │   ├ ApplicantFinderPanel.vue       지원자 찾기(접이식) + 지원번호 직접 입력
  │   ├ 조회 대상 칩                    선택된 지원번호, 해제 버튼
  │   ├ 필터 + 목록 + 페이저
  │   └ ApplicantEventDrawer.vue       상세
  └ [탭2] AuditLogPanel.vue
      ├ 필터 + 목록 + 페이저
      └ AuditLogDrawer.vue             상세
```

- 탭은 페이지 헤더 바로 아래에 둔다. `a-tabs` + lazy `a-tab-pane`.
- **URL 쿼리는 진입 시 한 번 읽기만 한다**(`?tab=client|audit`, `?applicationId=`). 다른 화면에서 넘어오는 입구용이다. 이후 탭·필터 변경을 URL에 되쓰지는 않는다 — 필터 전체를 직렬화해야 해서 얻는 것보다 비싸다. 새로고침하면 조회 전 상태로 돌아간다.
- **지원자 찾기와 조회 대상 칩은 지원자 이벤트 탭 안에만 있다.** 감사 로그는 관리자 행위 증적이지 지원자 스코프가 아니다.
- 두 탭의 조회 상태·필터는 서로 독립이다.

### 조회 전 상태

두 탭 모두 **진입 시 자동 조회하지 않는다.** 목록 헤더는 `조회 전`, 표 본문 대신 안내를 표시한다.

탭 버튼에 건수 배지는 두지 않는다. 두 탭의 필터가 독립이라 반대쪽 건수는 지금 보고 있는 조회 조건과 무관하고, 건수는 각 목록 헤더의 `총 N건`으로 이미 보인다.

| 탭 | 안내 |
|---|---|
| 지원자 이벤트 | "지원자를 먼저 선택하세요" — 지원자 찾기로 지원번호를 지정하거나, 세션 ID·오류 추적번호로 바로 조회 |
| 감사 로그 | "조회 조건을 지정한 뒤 **조회**를 누르세요" |

조회 상태로 바뀌는 계기: `조회` 버튼 / 지원자 선택 / 크로스 점프 도착 / 「같은 세션 이벤트 모두 보기」.
조회 전으로 되돌리는 계기: `초기화` 버튼 / 조회 대상 칩 `해제`. 지원자 이벤트 탭은 초기화 시 칩도 함께 푼다(칩이 곧 조회 조건이므로).

## 5. 지원자 찾기

두 로그 테이블에는 이름·휴대폰이 없다(감사 로그는 `applicantRefHash` HMAC만, 클라 이벤트는 `applicationId`까지만). `이름/휴대폰 → applicationId` 변환이 화면 안에 필요하다.

- 호출: `GET /api/admin/applications?name=&phoneNumber=&size=50` — [admin-application](../../domains/admin-application.md) 소유 엔드포인트, **계약 변경 없음**.
- `name` 부분일치, `phoneNumber`는 하이픈·공백 제거 후 숫자 부분일치.
- 둘 다 비어 있으면 호출하지 않고 "이름 또는 휴대폰 번호를 입력하세요" 안내.
- 결과 표 컬럼: `지원번호` · `이름` · `생년월일(나이)` · `공고 / 모집분야` · `상태`.
  - **휴대폰 컬럼은 없다.** `AdminApplicationSummaryResponse`에 휴대폰 필드가 없다(검색 조건으로만 쓰인다). 동명이인은 생년월일·공고로 구분한다.
  - 관리자 화면이므로 이름·생년월일은 마스킹하지 않는다.
- 행 클릭 → 조회 대상 칩 설정 + 찾기 패널 자동 접힘 + 즉시 조회.
- **지원번호 직접 입력칸은 항상 노출한다.** 파기된 지원자는 이름 스냅샷이 익명화돼 검색되지 않으므로 이 경로만 남는다.

## 6. 탭1 — 지원자 이벤트

### 필터
`기간`(기본 7일, 상한 90일, 프리셋 1/7/30/90일) · `이벤트`(14종) · `심각도`(INFO/WARN/ERROR) · `세션 ID`(완전일치) · `오류 추적번호`(`relatedCorrelationId` 완전일치)

`source`는 값이 `APPLICANT_WEB` 하나뿐이라 필터에 넣지 않는다.

조회 대상 칩이 있으면 `applicationId`가 함께 전달된다.

### 목록
| 수신시각 | 심각도 | 이벤트 | 메시지 코드 | HTTP | 화면 / API | 지원번호 |
|---|---|---|---|---|---|---|
| `receivedAt` | 색 태그 | `eventType` 한글 라벨 | `message` | `httpStatus` | `pageCode` ?? `apiPath` ?? `routePath` | `applicationId` |

정렬은 백엔드 고정(`receivedAt DESC, id DESC`). 행 클릭 = 상세.

### 상세 drawer
`ClientEventLogResponse` 전 필드를 5개 절로 나눈다: `이벤트` / `발생 위치` / `HTTP` / `스택 요약`(있을 때만) / `세션·단말` / `메타데이터`.
`stackSummary`·`metadataJson`은 고정폭 코드 블록으로 표시하고 `metadataJson`은 pretty print한다(파싱 실패 시 원문 그대로).

### 액션
- **「같은 세션 이벤트 모두 보기」**: `clientSessionId`를 필터에 넣고 **조회 대상 칩은 해제한다.** 한 세션에는 지원서 필드가 빈 행(전역 JS 오류·네트워크 오류 등)도 섞여 있어 칩을 유지하면 그 행들이 보이지 않는다.
- **「이 지원서 관련 감사 로그 보기」**(`applicationId`가 있을 때만): 감사 탭의 `지원번호` 필터를 채우고 탭 전환.

## 7. 탭2 — 감사 로그

### 필터
`기간`(기본 30일, 상한 90일, 프리셋 1/7/30/90일) · `행위`(`AuditActionType` 21종) · `결과`(5종) · `대상 유형`(11종) · `행위자 ID`(완전일치) · `공고 ID` · `지원번호`

백엔드가 각 조건을 단일 값으로만 받으므로 UI도 단일 선택이다(multi-select 없음).

### 목록
| 일시 | 행위자 | 행위 | 대상 | 결과 | 지원번호 | IP |
|---|---|---|---|---|---|---|
| `occurredAt` | `actorId` + `actorType` 라벨 | `actionType` 한글 라벨 | `targetType` + `targetId` | 색 태그 | `applicationId` | `ipAddress` 또는 `***` |

`actorType=SYSTEM` 행은 `actorId`가 스케줄러 식별자다.

### 상세 drawer
`AuditActivityResponse` 전 필드를 4개 절로: `행위` / `행위자` / `연결 정보` / `메타데이터`.

### 액션
- **「이 지원서의 지원자 이벤트 보기」**(`applicationId`가 있을 때만): 조회 대상 칩을 설정하고 탭 전환.
- `applicationId`가 없는 행(엑셀 반출·파기 배치·전형결과 발표 등)은 버튼 대신 "이 행위는 특정 지원서에 연결되어 있지 않습니다" 문구.

## 8. API 매핑

**백엔드 변경 없음.** 기존 🟢 엔드포인트만 쓴다.

| 화면 동작 | 엔드포인트 | 소유 카드 |
|---|---|---|
| 지원자 찾기 | `GET /admin/applications` | admin-application |
| 지원자 이벤트 목록 | `GET /admin/client-events` | client-event-log |
| 지원자 이벤트 상세 | `GET /admin/client-events/{id}` | client-event-log |
| 감사 로그 목록 | `GET /admin/audit/activities` | privacy-audit-audit |
| 감사 로그 상세 | `GET /admin/audit/activities/{id}` | privacy-audit-audit |

프론트가 지켜야 할 백엔드 가드:

| 규칙 | 감사 로그 | 지원자 이벤트 |
|---|---|---|
| `from` 기본값 | `to` − 30일 | `to` − 7일 |
| `to` 기본값 | now | now |
| 기간 상한 | `Duration(from, to) ≤ 90일` | 같음 |
| `from > to` | 400 | 400 |
| `page` | ≥ 0 | ≥ 0 |
| `size` | 1~100 | 1~100 |

**기간 상한은 날짜 개수가 아니라 `Duration`으로 판정된다.** 두 서비스 모두 `Duration.between(from, to) > Duration.ofDays(90)`이면 400이다(`toDays()`의 소수 절사를 피하려고 의도적으로 이렇게 짰다 — 09b 리뷰 반영).

화면은 날짜만 고르고 시각을 붙여 보낸다: `from = 시작일T00:00:00`, `to = 종료일T23:59:59`. 이 규칙에서는 **시작일·종료일 차이가 89일까지만 통과한다**(89일 + 23:59:59 < 90일, 90일 + 23:59:59 > 90일).

→ 화면 표기와 프리셋은 **시작일·종료일을 포함한 일수** 기준으로 맞춘다.

| 프리셋 | 시작일 | 차이 |
|---|---|---|
| 1일 | 종료일 | 0일 |
| 7일 | 종료일 − 6일 | 6일 |
| 30일 | 종료일 − 29일 | 29일 |
| 90일 | 종료일 − 89일 | 89일 |

라벨은 `기간 (시작일·종료일 포함 최대 90일)`. 기본값도 같은 규칙이다(감사 = 종료일 − 29일, 지원자 이벤트 = 종료일 − 6일).

- 화면에서 기간을 먼저 검증해 차이가 89일을 넘으면 조회를 보내지 않고 인라인 오류를 표시한다(서버 400을 그대로 띄우지 않는다).
- 페이지 크기는 20 고정. 응답은 공통 `PageResponse`.
- 목록 상세 조회는 목록 행의 데이터로 drawer를 먼저 채우지 말고 **단건 API를 호출한다**(목록과 단건의 마스킹 규칙이 같아 값 차이는 없지만, 단건 404로 "이미 삭제된 이벤트"를 구분할 수 있다).

## 9. 구현 계획

### 신규 파일

| 구분 | 경로 | 소유 카드 |
|---|---|---|
| view | `{FE}/views/admin/log/AdminLogView.vue` | client-event-log |
| view | `{FE}/views/admin/log/ApplicantEventPanel.vue` | client-event-log |
| view | `{FE}/views/admin/log/ApplicantFinderPanel.vue` | client-event-log |
| view | `{FE}/views/admin/log/ApplicantEventDrawer.vue` | client-event-log |
| view | `{FE}/views/admin/log/AuditLogPanel.vue` | privacy-audit-audit |
| view | `{FE}/views/admin/log/AuditLogDrawer.vue` | privacy-audit-audit |
| util | `{FE}/views/admin/log/logLabel.ts` | client-event-log |
| api | `{FE}/api/admin/adminClientEventApi.ts` | client-event-log |
| api | `{FE}/api/admin/adminAuditApi.ts` | privacy-audit-audit |
| types | `{FE}/types/admin/clientEventLog.ts` | client-event-log |
| types | `{FE}/types/admin/auditLog.ts` | privacy-audit-audit |

- `logLabel.ts`에 enum 한글 라벨 맵(`AuditActionType` 21 · `AuditTargetType` 11 · `AuditActionResult` 5 · `ActorType` 4 · client `eventType` 14)과 심각도·결과 태그 색을 모은다. [retentionLabel.ts](../../../recruit_front/src/views/admin/retention/retentionLabel.ts)와 같은 역할.
- 껍데기(`AdminLogView.vue`)는 client-event-log 카드가 소유하고, privacy-audit-audit 카드는 그 카드로 링크만 건다. 파일별 소유는 위 표대로 정확히 한 카드씩이다(`node tools/check-docs.mjs` 통과 조건).

### 수정 파일
- `{FE}/routes/adminRoutes.ts` — `{ path: 'logs', name: 'AdminLogInquiry', component: ... }` 추가. `meta`는 부모에서 상속하므로 별도 지정하지 않는다.

### 구현 순서
1. 타입 → API 모듈 → `logLabel.ts`
2. `AuditLogPanel` + `AuditLogDrawer` (지원자 찾기 의존 없음, 단독 검증 가능)
3. `ApplicantFinderPanel` → `ApplicantEventPanel` + `ApplicantEventDrawer`
4. `AdminLogView`(탭·권한 가드·URL 쿼리) + 라우트 등록
5. 크로스 점프 배선
6. 카드 갱신 + `node tools/check-docs.mjs`

## 10. 완료 후 남는 작업 (이 슬라이스 밖)

1. **사이드바 메뉴 등록** — 관리자 사이드바는 DB `menu` 테이블에서 렌더링한다. 코드로 추가되지 않는다. 배포 후 `/admin/menus` 화면에서 `ADMIN` 사이트에 소메뉴(`path=/admin/logs`)를 만들어야 사이드바에 뜬다. 메뉴가 없어도 URL 직접 입력으로는 동작한다.
2. **`ROLE_PRIVACY_ADMIN` 라우트 가드** — §3의 함정 2. `ADMIN_ROLES` 변경은 로그인 직후 이동 분기에도 영향을 주므로 별도 판단이 필요하다.
3. **지원현황 화면의 "로그 보기" 링크** — `/admin/logs?tab=client&applicationId={id}` 딥링크를 받는 쪽은 이 설계에 포함되지만, 링크를 거는 것은 admin-application 화면 변경이다.

## 11. 검증

- `cd recruit_front && npm run type-check` (기본)
- `npm run build` (필요 시)
- `node tools/check-docs.mjs` — 오류 0건. 신규 `views/**/*.vue` 6개가 각각 정확히 한 카드의 `## 파일 지도`에 있어야 한다.
- 백엔드 테스트는 돌리지 않는다(백엔드 변경 없음). **계약 영향 없음**을 보고에 명시한다.

수동 확인 항목:
- `ROLE_ADMIN`만 있는 계정으로 진입 시 `/403`으로 튕기지 않고 안내만 보이는가.
- 기간 91일 지정 시 서버 400이 아니라 화면 인라인 오류가 뜨는가.
- 파기된 지원자의 지원번호를 직접 입력했을 때 이벤트 목록이 정상 조회되는가.
- 두 탭의 필터·조회 상태가 탭 전환 후에도 각각 유지되는가.

## 12. 카드 갱신 (완료 조건)

| 카드 | 갱신 내용 |
|---|---|
| [client-event-log](../../domains/client-event-log.md) | `## 파일 지도` 프론트 절에 신규 파일 추가. "관리자 조회 API는 FE 화면 없음", "소유 화면 없음", `GET /admin/client-events` **FE 미사용** 표기를 모두 수정. |
| [privacy-audit-audit](../../domains/privacy-audit-audit.md) | `## 파일 지도` 프론트 절("없음")을 신규 파일로 교체. `## API 계약`의 "전 엔드포인트 FE 미사용" 문구 수정. 프론트 가드 함정(§3) 기록. |
| [_index.md](../../domains/_index.md) | 라우트 역색인에 `AdminLogInquiry` · `/admin/logs` 추가. |
| [admin-application](../../domains/admin-application.md) | `GET /admin/applications`의 "FE는 공고 경로만 쓴다" 문구에 로그 조회 화면 사용처 추가. |
