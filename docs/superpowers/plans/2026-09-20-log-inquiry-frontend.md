# 로그 조회 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 콘솔에 `/admin/logs` 화면을 추가해 지원자 브라우저 이벤트(`ClientEventLog`)와 관리자 감사 로그(`ActivityLog`)를 탭 2개로 조회한다.

**Architecture:** 프론트 전용 작업이다. 백엔드 조회 API 4개는 이미 있고 계약을 바꾸지 않는다. 껍데기 뷰 1개(`AdminLogView`)가 탭·권한 가드·탭 간 이동을 맡고, 탭마다 패널 컴포넌트 1개 + 상세 drawer 1개를 둔다. 라벨 매핑과 기간 계산은 순수 함수 파일로 빼서 vitest로 검증한다.

**Tech Stack:** Vue 3 `<script setup lang="ts">` · ant-design-vue 4 · Axios(`apiClient`) · Pinia(`authStore`) · vitest

**설계서:** `docs/superpowers/specs/2026-09-20-log-inquiry-design.md`
**목업:** `design/로그-조회.html`

---

## 선행 지식 (구현 전에 읽을 것)

| 문서 | 왜 |
|---|---|
| `docs/superpowers/specs/2026-09-20-log-inquiry-design.md` | 이 계획의 근거. 기각 항목·함정이 여기 있다 |
| `docs/domains/client-event-log.md` | 이벤트 14종·심각도·필드 의미 |
| `docs/domains/privacy-audit-audit.md` | 감사 enum·마스킹 규칙 |
| `recruit_front/AGENTS.md` | 프론트 규칙 |

**따라 할 기존 코드:**
- `recruit_front/src/views/admin/message/AdminMessageHistoryView.vue` — 기간 필터 + 목록 + 페이지 + drawer 패턴의 표준. 이 화면의 두 패널은 이 구조를 그대로 따른다.
- `recruit_front/src/views/admin/retention/AdminRetentionView.vue` — 탭 껍데기 + 권한 가드 + `nextTick` 후 자식 메서드 호출 패턴.
- `recruit_front/src/views/admin/retention/DataSubjectSearchPanel.vue` — 이름·휴대폰 검색 패널 패턴.
- `recruit_front/src/views/admin/retention/retentionLabel.ts` + `__tests__/retentionLabel.spec.ts` — 라벨 파일과 테스트 스타일.

**주의:**
- `@/types/api`와 `@/types/page` 둘 다 `PageResponse`를 갖고 있다. 기존 admin API 모듈은 `@/types/page`를 쓴다. **`@/types/page`를 쓴다.**
- `apiClient` 응답은 `response.data.data`로 꺼낸다(Axios `data` + `ApiResponse.data`).
- 목업(`design/로그-조회.html`)의 `개인정보 관리자 권한으로 보기` 체크박스는 데모용이다. 구현하지 않는다.

---

## File Structure

| 구분 | 경로 | 책임 |
|---|---|---|
| 타입 | `src/types/admin/clientEventLog.ts` | 지원자 이벤트 응답·조회 파라미터 |
| 타입 | `src/types/admin/auditLog.ts` | 감사 로그 응답·조회 파라미터·enum union |
| API | `src/api/admin/adminClientEventApi.ts` | `GET /admin/client-events`, `/{id}` |
| API | `src/api/admin/adminAuditApi.ts` | `GET /admin/audit/activities`, `/{id}` |
| API(수정) | `src/api/admin/adminApplicationApi.ts` | `searchApplicants` 추가 |
| 순수함수 | `src/views/admin/log/logQuery.ts` | 기간 프리셋·상한 검증·날짜→ISO 변환 |
| 순수함수 | `src/views/admin/log/logLabel.ts` | enum → 한글 라벨, 태그 색 |
| 뷰 | `src/views/admin/log/AdminLogView.vue` | 페이지 껍데기·탭·권한 가드·탭 간 이동 |
| 뷰 | `src/views/admin/log/ApplicantEventPanel.vue` | 탭1 본문 |
| 뷰 | `src/views/admin/log/ApplicantFinderPanel.vue` | 지원자 찾기 |
| 뷰 | `src/views/admin/log/ApplicantEventDrawer.vue` | 탭1 상세 |
| 뷰 | `src/views/admin/log/AuditLogPanel.vue` | 탭2 본문 |
| 뷰 | `src/views/admin/log/AuditLogDrawer.vue` | 탭2 상세 |
| 라우트(수정) | `src/routes/adminRoutes.ts` | `/admin/logs` 등록 |
| 테스트 | `src/views/admin/log/__tests__/logQuery.spec.ts` | 기간 계산 |
| 테스트 | `src/views/admin/log/__tests__/logLabel.spec.ts` | 라벨 매핑 |

### 컴포넌트 간 계약 (먼저 고정한다)

```
AdminLogView
  activeTab: 'client' | 'audit'
  ├ ApplicantEventPanel  ref=applicantPanelRef
  │    emits: (e: 'open-audit', applicationId: number)
  │    expose: applyApplicationId(applicationId: number): void
  └ AuditLogPanel        ref=auditPanelRef
       emits: (e: 'open-applicant-events', applicationId: number)
       expose: applyApplicationId(applicationId: number): void
```

`a-tab-pane`은 처음 활성화되기 전까지 렌더링하지 않는다(lazy). 그래서 탭 이동은 **`activeTab`을 바꾼 뒤 `await nextTick()` 하고** 자식의 `applyApplicationId`를 호출한다. `AdminRetentionView.vue`의 `onOpenBatch`와 같은 이유다.

---

### Task 1: 타입 정의

**Files:**
- Create: `recruit_front/src/types/admin/clientEventLog.ts`
- Create: `recruit_front/src/types/admin/auditLog.ts`

- [ ] **Step 1: `clientEventLog.ts` 작성**

`eventType`·`severity`·`source`는 이미 `@/types/clientEvent`에 있다. 새로 정의하지 말고 재사용한다.

```ts
import type { ClientEventSeverity, ClientEventSource, ClientEventType } from '@/types/clientEvent'

/**
 * GET /api/admin/client-events 응답 1행. 백엔드 ClientEventLogResponse 와 1:1이다.
 * ipAddress·userAgent·principalHash·principalType 은 ROLE_PRIVACY_ADMIN 이 아니면
 * 서버가 "***" 로 바꿔 보낸다(원래 null 이면 null). 프론트는 판정하지 않고 받은 값을 그대로 쓴다.
 */
export interface ClientEventLogResponse {
  id: number
  receivedAt: string
  clientOccurredAt: string | null
  eventType: ClientEventType
  severity: ClientEventSeverity
  source: ClientEventSource
  clientSessionId: string
  clientEventId: string
  ingestCorrelationId: string | null
  relatedCorrelationId: string | null
  pageCode: string | null
  componentCode: string | null
  routePath: string | null
  operation: string | null
  jobPostingId: number | null
  applicationId: number | null
  httpMethod: string | null
  apiPath: string | null
  httpStatus: number | null
  errorCode: string | null
  message: string | null
  stackHash: string | null
  stackSummary: string | null
  frontendVersion: string | null
  browserName: string | null
  browserVersion: string | null
  osName: string | null
  viewport: string | null
  timezone: string | null
  ipAddress: string | null
  userAgent: string | null
  principalHash: string | null
  principalType: 'APPLICANT' | 'EMPLOYEE' | null
  /** metadata 를 직렬화한 JSON 문자열 그대로. 객체가 아니다. */
  metadataJson: string | null
}

/** GET /api/admin/client-events 쿼리. from·to 는 ISO date-time(yyyy-MM-ddTHH:mm:ss). */
export interface ClientEventLogQuery {
  eventType?: ClientEventType
  severity?: ClientEventSeverity
  applicationId?: number
  jobPostingId?: number
  clientSessionId?: string
  relatedCorrelationId?: string
  from?: string
  to?: string
  page: number
  size: number
}
```

- [ ] **Step 2: `auditLog.ts` 작성**

```ts
export type AuditActorType = 'EMPLOYEE' | 'SYSTEM' | 'APPLICANT' | 'ANONYMOUS'

export type AuditActionResult = 'SUCCESS' | 'FAILURE' | 'DENIED' | 'SKIPPED' | 'CONFLICT'

export type AuditActionType =
  | 'EXPORT_APPLICATIONS'
  | 'EXPORT_STAGE_RESULTS'
  | 'EXPORT_INTERVIEWS'
  | 'EXPORT_EVALUATIONS'
  | 'EXPORT_STAGE_RESULT_TEMPLATE'
  | 'APPLICATION_PDF'
  | 'STAGE_RESULT_UPLOAD'
  | 'STAGE_RESULT_CORRECT'
  | 'STAGE_RESULT_ANNOUNCE'
  | 'STAGE_RESULT_CONFIRM'
  | 'EVALUATION_REOPEN'
  | 'ATTACHMENT_ADMIN_DOWNLOAD'
  | 'ATTACHMENT_ADMIN_DELETE'
  | 'RETENTION_POLICY_UPDATE'
  | 'RETENTION_HOLD_SET'
  | 'RETENTION_HOLD_RELEASE'
  | 'RETENTION_ANCHOR_SET'
  | 'PURGE_SCAN'
  | 'PURGE_EXECUTE'
  | 'PURGE_RECONCILE'
  | 'PURGE_FORCED'

export type AuditTargetType =
  | 'STAGE_RESULT'
  | 'JOB_APPLICATION'
  | 'APPLICATION_ATTACHMENT'
  | 'INTERVIEW_EVALUATION'
  | 'EXPORT_DATASET'
  | 'APPLICATION_PDF'
  | 'RETENTION_POLICY'
  | 'RETENTION_HOLD'
  | 'JOB_POSTING'
  | 'PURGE_BATCH'
  | 'RETENTION_SCHEDULE'

/**
 * GET /api/admin/audit/activities 응답 1행. 백엔드 AuditActivityResponse 와 1:1이다.
 * ipAddress·userAgent 는 ROLE_PRIVACY_ADMIN 이 아니면 서버가 "***" 로 바꿔 보낸다.
 * reasonCode 는 AuditReasonCode enum 이름이지만, 값이 늘어나도 화면이 깨지지 않게 string 으로 받는다.
 */
export interface AuditActivityResponse {
  id: number
  occurredAt: string
  actorType: AuditActorType
  actorId: string | null
  actorRoleSnapshot: string | null
  actionType: AuditActionType
  actionResult: AuditActionResult
  targetType: AuditTargetType
  targetId: string | null
  jobPostingId: number | null
  applicationId: number | null
  applicantRefHash: string | null
  reasonCode: string | null
  reasonMessage: string | null
  correlationId: string | null
  ipAddress: string | null
  userAgent: string | null
  metadataJson: string | null
}

/** GET /api/admin/audit/activities 쿼리. from·to 는 ISO date-time(yyyy-MM-ddTHH:mm:ss). */
export interface AuditActivityQuery {
  actorId?: string
  actionType?: AuditActionType
  actionResult?: AuditActionResult
  targetType?: AuditTargetType
  jobPostingId?: number
  applicationId?: number
  from?: string
  to?: string
  page: number
  size: number
}
```

- [ ] **Step 3: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 4: 커밋**

```bash
git add recruit_front/src/types/admin/clientEventLog.ts recruit_front/src/types/admin/auditLog.ts
git commit -m "feat(client-event-log): 로그 조회 화면 응답·쿼리 타입 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: API 모듈

**Files:**
- Create: `recruit_front/src/api/admin/adminClientEventApi.ts`
- Create: `recruit_front/src/api/admin/adminAuditApi.ts`
- Modify: `recruit_front/src/api/admin/adminApplicationApi.ts` (`adminApplicationApi` 객체에 메서드 1개 추가)

- [ ] **Step 1: `adminClientEventApi.ts` 작성**

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { ClientEventLogQuery, ClientEventLogResponse } from '@/types/admin/clientEventLog'

/**
 * 지원자 이벤트(클라이언트 이벤트 로그) 조회. ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다.
 * ROLE_ADMIN 만 가진 계정이 호출하면 403 이고, 공통 인터셉터가 /403 으로 보내 버린다.
 * 화면에서 권한을 먼저 확인하고 호출해야 한다.
 */
export const adminClientEventApi = {
  /** 목록(페이지). 정렬은 서버 고정(receivedAt DESC, id DESC). */
  getClientEvents(query: ClientEventLogQuery) {
    return apiClient.get<ApiResponse<PageResponse<ClientEventLogResponse>>>('/admin/client-events', {
      params: query,
    })
  },

  /** 단건. 없으면 404. */
  getClientEvent(id: number) {
    return apiClient.get<ApiResponse<ClientEventLogResponse>>(`/admin/client-events/${id}`)
  },
}
```

- [ ] **Step 2: `adminAuditApi.ts` 작성**

```ts
import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { AuditActivityQuery, AuditActivityResponse } from '@/types/admin/auditLog'

/**
 * 감사 로그 조회. ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다.
 * 기록 API 는 없다(각 도메인 서비스가 내부에서 남긴다).
 */
export const adminAuditApi = {
  /** 목록(페이지). 정렬은 서버 고정(occurredAt DESC, id DESC). */
  getActivities(query: AuditActivityQuery) {
    return apiClient.get<ApiResponse<PageResponse<AuditActivityResponse>>>('/admin/audit/activities', {
      params: query,
    })
  },

  /** 단건. 없으면 404. */
  getActivity(id: number) {
    return apiClient.get<ApiResponse<AuditActivityResponse>>(`/admin/audit/activities/${id}`)
  },
}
```

- [ ] **Step 3: `adminApplicationApi.ts`에 `searchApplicants` 추가**

`getApplications`는 공고 경로(`/admin/job-postings/{id}/applications`)라 공고를 모르는 지원자 찾기에 쓸 수 없다. 공고 무관 경로를 쓰는 메서드를 하나 더 둔다.

`adminApplicationApi` 객체 안, `getApplications` **바로 아래**에 넣는다:

```ts
  /**
   * 이름·휴대폰으로 지원서를 찾는다(공고 무관). 로그 조회 화면의 "지원자 찾기" 전용.
   * name 부분일치, phoneNumber 는 하이픈·공백을 뺀 숫자 부분일치.
   * 응답에 휴대폰 필드는 없다(검색 조건으로만 쓴다).
   */
  searchApplicants(name: string | undefined, phoneNumber: string | undefined, size = 50) {
    return apiClient.get<ApiResponse<PageResponse<AdminApplicationSummaryResponse>>>('/admin/applications', {
      params: { name, phoneNumber, page: 0, size },
    })
  },
```

- [ ] **Step 4: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 5: 커밋**

```bash
git add recruit_front/src/api/admin/adminClientEventApi.ts recruit_front/src/api/admin/adminAuditApi.ts recruit_front/src/api/admin/adminApplicationApi.ts
git commit -m "feat(client-event-log): 로그 조회 API 모듈 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 기간 계산 (`logQuery.ts`) — TDD

두 탭이 같은 기간 규칙을 쓴다. 백엔드는 `Duration.between(from, to) > 90일`이면 400이다. 화면은 날짜만 고르고 `시작일T00:00:00` ~ `종료일T23:59:59`를 보내므로 **시작일·종료일 차이는 89일까지만** 통과한다(= 포함 일수 90일).

**Files:**
- Create: `recruit_front/src/views/admin/log/logQuery.ts`
- Test: `recruit_front/src/views/admin/log/__tests__/logQuery.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

```ts
import { describe, expect, it } from 'vitest'

import { MAX_RANGE_DAYS, presetRange, rangeError, toDateTimeRange } from '../logQuery'

describe('logQuery', () => {
  it('1일 프리셋은 종료일 하루만 고른다', () => {
    expect(presetRange(1, new Date(2026, 8, 20))).toEqual(['2026-09-20', '2026-09-20'])
  })

  it('7일 프리셋은 종료일을 포함해 7일이다', () => {
    expect(presetRange(7, new Date(2026, 8, 20))).toEqual(['2026-09-14', '2026-09-20'])
  })

  it('30일 프리셋은 종료일을 포함해 30일이다', () => {
    expect(presetRange(30, new Date(2026, 8, 20))).toEqual(['2026-08-22', '2026-09-20'])
  })

  it('90일 프리셋은 종료일을 포함해 90일이다(차이 89일)', () => {
    expect(presetRange(MAX_RANGE_DAYS, new Date(2026, 8, 20))).toEqual(['2026-06-23', '2026-09-20'])
  })

  it('월을 넘어가도 날짜를 정확히 센다', () => {
    expect(presetRange(7, new Date(2026, 0, 3))).toEqual(['2025-12-28', '2026-01-03'])
  })

  it('시작일이 종료일보다 늦으면 오류 메시지를 준다', () => {
    expect(rangeError('2026-09-21', '2026-09-20')).toBe('시작일이 종료일보다 늦습니다.')
  })

  it('포함 일수 90일까지는 통과한다', () => {
    expect(rangeError('2026-06-23', '2026-09-20')).toBeNull()
  })

  it('포함 일수 91일이면 오류 메시지를 준다', () => {
    expect(rangeError('2026-06-22', '2026-09-20')).toBe('조회 기간은 시작일·종료일 포함 90일까지입니다.')
  })

  it('같은 날이면 통과한다', () => {
    expect(rangeError('2026-09-20', '2026-09-20')).toBeNull()
  })

  it('날짜를 서버가 받는 ISO date-time 으로 바꾼다', () => {
    expect(toDateTimeRange(['2026-09-14', '2026-09-20'])).toEqual({
      from: '2026-09-14T00:00:00',
      to: '2026-09-20T23:59:59',
    })
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `cd recruit_front && npx vitest run src/views/admin/log/__tests__/logQuery.spec.ts`
Expected: FAIL — `Failed to resolve import "../logQuery"`

- [ ] **Step 3: 구현**

```ts
import { formatDate } from '@/common/dateUtil'

/**
 * 로그 조회 두 탭이 공유하는 기간 규칙.
 *
 * 백엔드(AuditActivityReadService·ClientEventLogReadService)는 Duration.between(from, to) 이
 * 90일을 넘으면 400 을 낸다. 화면은 날짜만 고르고 시작일T00:00:00 ~ 종료일T23:59:59 를 보내므로
 * 날짜 차이가 89일이면 89일 23:59:59 로 통과하고, 90일이면 90일 23:59:59 라 400 이 난다.
 * 그래서 화면 표기·프리셋·검증은 모두 "시작일·종료일을 포함한 일수" 기준이다.
 */

export const MAX_RANGE_DAYS = 90

export type DateRange = [string, string]

/** 프리셋 버튼(포함 일수). */
export const RANGE_PRESETS = [1, 7, 30, MAX_RANGE_DAYS] as const

const MS_PER_DAY = 24 * 60 * 60 * 1000

const toDateOnly = (value: string): Date => {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** 종료일을 today 로 두고, 그 날을 포함해 days 일치 범위를 만든다. */
export const presetRange = (days: number, today: Date): DateRange => {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return [formatDate(from), formatDate(today)]
}

/** 시작일·종료일을 포함한 일수. 같은 날이면 1이다. */
export const rangeDays = (from: string, to: string): number =>
  Math.round((toDateOnly(to).getTime() - toDateOnly(from).getTime()) / MS_PER_DAY) + 1

/** 조회 전에 부르는 검증. 통과하면 null, 아니면 화면에 띄울 한글 메시지. */
export const rangeError = (from: string, to: string): string | null => {
  const days = rangeDays(from, to)
  if (days <= 0) return '시작일이 종료일보다 늦습니다.'
  if (days > MAX_RANGE_DAYS) return `조회 기간은 시작일·종료일 포함 ${MAX_RANGE_DAYS}일까지입니다.`
  return null
}

/** 날짜 범위를 서버가 받는 ISO date-time 으로 바꾼다. */
export const toDateTimeRange = (range: DateRange): { from: string; to: string } => ({
  from: `${range[0]}T00:00:00`,
  to: `${range[1]}T23:59:59`,
})
```

- [ ] **Step 4: 통과 확인**

Run: `cd recruit_front && npx vitest run src/views/admin/log/__tests__/logQuery.spec.ts`
Expected: PASS — 10 passed

- [ ] **Step 5: 커밋**

```bash
git add recruit_front/src/views/admin/log/logQuery.ts recruit_front/src/views/admin/log/__tests__/logQuery.spec.ts
git commit -m "feat(client-event-log): 로그 조회 기간 프리셋·상한 검증 유틸 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: 라벨 매핑 (`logLabel.ts`) — TDD

**Files:**
- Create: `recruit_front/src/views/admin/log/logLabel.ts`
- Test: `recruit_front/src/views/admin/log/__tests__/logLabel.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

```ts
import { describe, expect, it } from 'vitest'

import {
  ACTION_TYPE_OPTIONS,
  EVENT_TYPE_OPTIONS,
  TARGET_TYPE_OPTIONS,
  actionResultTag,
  actionTypeLabel,
  actorTypeLabel,
  eventTypeLabel,
  severityColor,
  targetTypeLabel,
} from '../logLabel'

describe('logLabel', () => {
  it('감사 행위를 한글로 준다', () => {
    expect(actionTypeLabel('EXPORT_APPLICATIONS')).toBe('지원현황 엑셀 반출')
    expect(actionTypeLabel('PURGE_FORCED')).toBe('강제 파기')
  })

  it('감사 대상 유형을 한글로 준다', () => {
    expect(targetTypeLabel('RETENTION_SCHEDULE')).toBe('파기 스케줄')
  })

  it('행위자 유형을 한글로 준다', () => {
    expect(actorTypeLabel('SYSTEM')).toBe('시스템')
  })

  it('감사 결과는 라벨과 태그 색을 함께 준다', () => {
    expect(actionResultTag('SUCCESS')).toEqual({ label: '성공', color: 'green' })
    expect(actionResultTag('CONFLICT')).toEqual({ label: '충돌', color: 'orange' })
    expect(actionResultTag('SKIPPED')).toEqual({ label: '건너뜀', color: 'default' })
  })

  it('이벤트 유형을 한글로 준다', () => {
    expect(eventTypeLabel('APPLICATION_DRAFT_SAVE_FAILED')).toBe('임시저장 실패')
  })

  it('심각도별 태그 색을 준다', () => {
    expect(severityColor('ERROR')).toBe('red')
    expect(severityColor('WARN')).toBe('orange')
    expect(severityColor('INFO')).toBe('blue')
  })

  it('select 옵션은 코드 전부를 담는다', () => {
    expect(ACTION_TYPE_OPTIONS).toHaveLength(21)
    expect(TARGET_TYPE_OPTIONS).toHaveLength(11)
    expect(EVENT_TYPE_OPTIONS).toHaveLength(14)
  })

  it('select 옵션은 value·label 모양이다', () => {
    expect(EVENT_TYPE_OPTIONS[0]).toEqual({ value: 'PAGE_OPENED', label: '화면 진입' })
  })
})
```

- [ ] **Step 2: 실패 확인**

Run: `cd recruit_front && npx vitest run src/views/admin/log/__tests__/logLabel.spec.ts`
Expected: FAIL — `Failed to resolve import "../logLabel"`

- [ ] **Step 3: 구현**

```ts
import type { ClientEventSeverity, ClientEventType } from '@/types/clientEvent'
import type {
  AuditActionResult,
  AuditActionType,
  AuditActorType,
  AuditTargetType,
} from '@/types/admin/auditLog'

/*
 * 로그 조회 화면의 코드 → 한글 라벨. 목록·상세·필터 select 가 같은 라벨을 써야 해서 한 곳에 모은다.
 * 백엔드 enum 에 값이 늘면 여기와 아래 옵션 개수 테스트를 같이 고친다.
 */

export interface LogTag {
  label: string
  color: string
}

const toOptions = <T extends string>(labels: Record<T, string>): { value: T; label: string }[] =>
  (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }))

const ACTION_TYPE_LABEL: Record<AuditActionType, string> = {
  EXPORT_APPLICATIONS: '지원현황 엑셀 반출',
  EXPORT_STAGE_RESULTS: '전형결과 반출',
  EXPORT_INTERVIEWS: '면접 명단 반출',
  EXPORT_EVALUATIONS: '면접평가 반출',
  EXPORT_STAGE_RESULT_TEMPLATE: '업로드 양식 반출',
  APPLICATION_PDF: '지원서 PDF 생성',
  STAGE_RESULT_UPLOAD: '전형결과 업로드',
  STAGE_RESULT_CORRECT: '전형결과 정정',
  STAGE_RESULT_ANNOUNCE: '전형결과 발표',
  STAGE_RESULT_CONFIRM: '전형결과 확정',
  EVALUATION_REOPEN: '면접평가 재오픈',
  ATTACHMENT_ADMIN_DOWNLOAD: '첨부파일 다운로드',
  ATTACHMENT_ADMIN_DELETE: '첨부파일 삭제',
  RETENTION_POLICY_UPDATE: '보존정책 변경',
  RETENTION_HOLD_SET: '보존 보류 설정',
  RETENTION_HOLD_RELEASE: '보존 보류 해제',
  RETENTION_ANCHOR_SET: '보존 기준일 설정',
  PURGE_SCAN: '파기 대상 산정',
  PURGE_EXECUTE: '파기 실행',
  PURGE_RECONCILE: '파기 재처리',
  PURGE_FORCED: '강제 파기',
}

const TARGET_TYPE_LABEL: Record<AuditTargetType, string> = {
  STAGE_RESULT: '전형결과',
  JOB_APPLICATION: '지원서',
  APPLICATION_ATTACHMENT: '첨부파일',
  INTERVIEW_EVALUATION: '면접평가',
  EXPORT_DATASET: '반출 데이터셋',
  APPLICATION_PDF: '지원서 PDF',
  RETENTION_POLICY: '보존정책',
  RETENTION_HOLD: '보존 보류',
  JOB_POSTING: '공고',
  PURGE_BATCH: '파기 배치',
  RETENTION_SCHEDULE: '파기 스케줄',
}

const ACTOR_TYPE_LABEL: Record<AuditActorType, string> = {
  EMPLOYEE: '직원',
  SYSTEM: '시스템',
  APPLICANT: '지원자',
  ANONYMOUS: '비로그인',
}

const ACTION_RESULT_TAG: Record<AuditActionResult, LogTag> = {
  SUCCESS: { label: '성공', color: 'green' },
  FAILURE: { label: '실패', color: 'red' },
  DENIED: { label: '거부', color: 'red' },
  SKIPPED: { label: '건너뜀', color: 'default' },
  CONFLICT: { label: '충돌', color: 'orange' },
}

const EVENT_TYPE_LABEL: Record<ClientEventType, string> = {
  PAGE_OPENED: '화면 진입',
  CHECKPOINT: '체크포인트',
  API_ERROR: 'API 오류',
  API_TIMEOUT: 'API 타임아웃',
  NETWORK_ERROR: '네트워크 오류',
  SESSION_EXPIRED: '세션 만료',
  FORBIDDEN: '권한 없음',
  JS_ERROR: '스크립트 오류',
  UNHANDLED_REJECTION: '미처리 Promise',
  APPLICATION_DRAFT_SAVE_FAILED: '임시저장 실패',
  APPLICATION_SUBMIT_CLICKED: '제출 클릭',
  APPLICATION_SUBMIT_FAILED: '제출 실패',
  ATTACHMENT_UPLOAD_FAILED: '첨부 업로드 실패',
  CLIENT_VALIDATION_FAILED: '입력검증 실패',
}

const SEVERITY_COLOR: Record<ClientEventSeverity, string> = {
  ERROR: 'red',
  WARN: 'orange',
  INFO: 'blue',
}

export const actionTypeLabel = (value: AuditActionType): string => ACTION_TYPE_LABEL[value]
export const targetTypeLabel = (value: AuditTargetType): string => TARGET_TYPE_LABEL[value]
export const actorTypeLabel = (value: AuditActorType): string => ACTOR_TYPE_LABEL[value]
export const actionResultTag = (value: AuditActionResult): LogTag => ACTION_RESULT_TAG[value]
export const eventTypeLabel = (value: ClientEventType): string => EVENT_TYPE_LABEL[value]
export const severityColor = (value: ClientEventSeverity): string => SEVERITY_COLOR[value]

export const ACTION_TYPE_OPTIONS = toOptions(ACTION_TYPE_LABEL)
export const TARGET_TYPE_OPTIONS = toOptions(TARGET_TYPE_LABEL)
export const EVENT_TYPE_OPTIONS = toOptions(EVENT_TYPE_LABEL)
export const ACTION_RESULT_OPTIONS = (Object.keys(ACTION_RESULT_TAG) as AuditActionResult[]).map((value) => ({
  value,
  label: ACTION_RESULT_TAG[value].label,
}))
export const SEVERITY_OPTIONS = (Object.keys(SEVERITY_COLOR) as ClientEventSeverity[]).map((value) => ({
  value,
  label: value,
}))

/** metadataJson·stackSummary 를 상세에서 읽기 좋게 편다. 파싱 실패하면 원문 그대로. */
export const prettyJson = (value: string | null): string => {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd recruit_front && npx vitest run src/views/admin/log/__tests__/logLabel.spec.ts`
Expected: PASS — 8 passed

- [ ] **Step 5: 커밋**

```bash
git add recruit_front/src/views/admin/log/logLabel.ts recruit_front/src/views/admin/log/__tests__/logLabel.spec.ts
git commit -m "feat(client-event-log): 로그 조회 라벨 매핑 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: 감사 로그 상세 drawer

**Files:**
- Create: `recruit_front/src/views/admin/log/AuditLogDrawer.vue`

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { adminAuditApi } from '@/api/admin/adminAuditApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { AuditActivityResponse } from '@/types/admin/auditLog'
import { actionResultTag, actionTypeLabel, actorTypeLabel, prettyJson, targetTypeLabel } from './logLabel'

/*
 * 감사 로그 1건 상세. 목록 행 데이터를 쓰지 않고 단건 API 를 다시 부른다.
 * 값은 같지만 404 로 "없는 행"을 구분할 수 있고, 목록 페이지가 바뀌어도 drawer 내용이 흔들리지 않는다.
 */

const props = defineProps<{ open: boolean; activityId: number | null }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'open-applicant-events', applicationId: number): void
}>()

const activity = ref<AuditActivityResponse | null>(null)
const loading = ref(false)

const load = async (id: number): Promise<void> => {
  loading.value = true
  try {
    const response = await adminAuditApi.getActivity(id)
    activity.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '감사 로그를 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.activityId] as const,
  ([open, id]) => {
    if (open && id !== null) {
      void load(id)
    }
    if (!open) {
      activity.value = null
    }
  },
  { immediate: true },
)

const close = (): void => emit('update:open', false)

const goApplicantEvents = (): void => {
  if (activity.value?.applicationId == null) return
  emit('open-applicant-events', activity.value.applicationId)
  close()
}
</script>

<template>
  <a-drawer
    :open="props.open"
    :width="760"
    :title="activity ? `감사 로그 #${activity.id}` : '감사 로그'"
    @close="close"
  >
    <a-spin :spinning="loading">
      <template v-if="activity">
        <a-descriptions title="행위" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="일시">{{ formatDate(activity.occurredAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="행위">
            {{ actionTypeLabel(activity.actionType) }}
            <span class="code">{{ activity.actionType }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="결과">
            <a-tag :color="actionResultTag(activity.actionResult).color">
              {{ actionResultTag(activity.actionResult).label }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="대상">
            {{ targetTypeLabel(activity.targetType) }}
            <span v-if="activity.targetId" class="code">{{ activity.targetId }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="사유 코드">{{ activity.reasonCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사유 메시지">{{ activity.reasonMessage ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="행위자" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="행위자 ID">{{ activity.actorId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="행위자 유형">{{ actorTypeLabel(activity.actorType) }}</a-descriptions-item>
          <a-descriptions-item label="행위 시점 권한">{{ activity.actorRoleSnapshot ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="IP 주소">{{ activity.ipAddress ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="User-Agent">{{ activity.userAgent ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="연결 정보" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="공고 ID">{{ activity.jobPostingId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원번호">{{ activity.applicationId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원자 가명키">{{ activity.applicantRefHash ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="추적번호">{{ activity.correlationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="section">
          <h4 class="section-title">메타데이터</h4>
          <pre v-if="activity.metadataJson" class="json">{{ prettyJson(activity.metadataJson) }}</pre>
          <p v-else class="empty-text">없음</p>
        </div>
      </template>
    </a-spin>

    <template #footer>
      <div class="footer">
        <a-button v-if="activity?.applicationId != null" @click="goApplicantEvents">
          이 지원서의 지원자 이벤트 보기
        </a-button>
        <span v-else class="empty-text">이 행위는 특정 지원서에 연결되어 있지 않습니다</span>
        <a-button @click="close">닫기</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.section {
  margin-bottom: 18px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
}

.code {
  margin-left: 6px;
  font-family: monospace;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.json {
  margin: 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
}

.empty-text {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
</style>
```

- [ ] **Step 2: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 3: 커밋**

```bash
git add recruit_front/src/views/admin/log/AuditLogDrawer.vue
git commit -m "feat(privacy-audit-audit): 감사 로그 상세 drawer 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: 감사 로그 패널

**Files:**
- Create: `recruit_front/src/views/admin/log/AuditLogPanel.vue`

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'

import { adminAuditApi } from '@/api/admin/adminAuditApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type {
  AuditActionResult,
  AuditActionType,
  AuditActivityQuery,
  AuditActivityResponse,
  AuditTargetType,
} from '@/types/admin/auditLog'
import AuditLogDrawer from './AuditLogDrawer.vue'
import { ACTION_RESULT_OPTIONS, ACTION_TYPE_OPTIONS, TARGET_TYPE_OPTIONS, actionResultTag, actionTypeLabel, actorTypeLabel, targetTypeLabel } from './logLabel'
import { RANGE_PRESETS, type DateRange, presetRange, rangeError, toDateTimeRange } from './logQuery'

/*
 * 감사 로그 탭. 관리자·시스템의 서버 행위 증적이라 지원자 스코프가 아니다.
 * 지원자 찾기는 이 탭에 없고, 지원번호는 필터 입력칸으로 직접 받는다.
 * 진입 시 자동 조회하지 않는다(조회 버튼을 눌러야 첫 조회).
 */

const PAGE_SIZE = 20
const DEFAULT_RANGE_DAYS = 30

const emit = defineEmits<{ (e: 'open-applicant-events', applicationId: number): void }>()

const range = ref<DateRange>(presetRange(DEFAULT_RANGE_DAYS, new Date()))
const actionType = ref<AuditActionType | undefined>(undefined)
const actionResult = ref<AuditActionResult | undefined>(undefined)
const targetType = ref<AuditTargetType | undefined>(undefined)
const actorId = ref('')
const jobPostingId = ref('')
const applicationId = ref('')

const rows = ref<AuditActivityResponse[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
const searched = ref(false)
/* 조회 버튼을 누른 시점의 조건. 페이지 이동은 이 조건으로 다시 읽는다. */
const appliedQuery = ref<Omit<AuditActivityQuery, 'page' | 'size'>>({})

const columns = [
  { title: '일시', key: 'occurredAt', width: 160 },
  { title: '행위자', key: 'actor', width: 170 },
  { title: '행위', key: 'actionType', width: 190 },
  { title: '대상', key: 'target', ellipsis: true },
  { title: '결과', key: 'actionResult', width: 90 },
  { title: '지원번호', key: 'applicationId', width: 100 },
  { title: 'IP', key: 'ipAddress', width: 140 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const asRow = (record: unknown): AuditActivityResponse => record as AuditActivityResponse

const toNumber = (value: string): number | undefined => {
  const trimmed = value.trim()
  if (!trimmed) return undefined
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : undefined
}

const buildQuery = (): Omit<AuditActivityQuery, 'page' | 'size'> => ({
  ...toDateTimeRange(range.value),
  actionType: actionType.value,
  actionResult: actionResult.value,
  targetType: targetType.value,
  actorId: actorId.value.trim() || undefined,
  jobPostingId: toNumber(jobPostingId.value),
  applicationId: toNumber(applicationId.value),
})

let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await adminAuditApi.getActivities({
      ...appliedQuery.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
    searched.value = true
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '감사 로그를 불러오지 못했습니다.'))
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

const search = (): void => {
  const error = rangeError(range.value[0], range.value[1])
  if (error !== null) {
    message.warning(error)
    return
  }
  appliedQuery.value = buildQuery()
  page.value = 0
  void loadList()
}

const reset = (): void => {
  range.value = presetRange(DEFAULT_RANGE_DAYS, new Date())
  actionType.value = undefined
  actionResult.value = undefined
  targetType.value = undefined
  actorId.value = ''
  jobPostingId.value = ''
  applicationId.value = ''
  rows.value = []
  totalElements.value = 0
  page.value = 0
  searched.value = false
}

const applyPreset = (days: number): void => {
  range.value = presetRange(days, new Date())
}

const onRangeChange = (_: unknown, dateStrings: [string, string]): void => {
  if (dateStrings[0] && dateStrings[1]) {
    range.value = dateStrings
  }
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const openDetail = (row: AuditActivityResponse): void => {
  detailId.value = row.id
  drawerOpen.value = true
}

const onRow = (record: unknown) => ({ onClick: () => openDetail(asRow(record)) })

/** 지원자 이벤트 탭에서 넘어올 때: 다른 조건을 비우고 지원번호만 걸어 바로 조회한다. */
const applyApplicationId = async (targetApplicationId: number): Promise<void> => {
  reset()
  applicationId.value = String(targetApplicationId)
  await nextTick()
  search()
}

defineExpose({ applyApplicationId })
</script>

<template>
  <div class="audit-panel">
    <section class="filters">
      <div class="filter-row">
        <label class="filter-label">기간 (시작일·종료일 포함 최대 90일)</label>
        <a-range-picker
          :value="range"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :placeholder="['시작일', '종료일']"
          @change="onRangeChange"
        />
        <a-space :size="4" class="presets">
          <a-button v-for="days in RANGE_PRESETS" :key="days" size="small" @click="applyPreset(days)">
            {{ days }}일
          </a-button>
        </a-space>
      </div>

      <div class="filter-row">
        <a-select
          v-model:value="actionType"
          :options="ACTION_TYPE_OPTIONS"
          placeholder="전체 행위"
          allow-clear
          class="filter-select"
        />
        <a-select
          v-model:value="actionResult"
          :options="ACTION_RESULT_OPTIONS"
          placeholder="전체 결과"
          allow-clear
          class="filter-select-sm"
        />
        <a-select
          v-model:value="targetType"
          :options="TARGET_TYPE_OPTIONS"
          placeholder="전체 대상"
          allow-clear
          class="filter-select-sm"
        />
        <a-input v-model:value="actorId" placeholder="행위자 ID" allow-clear class="filter-input" @press-enter="search" />
        <a-input v-model:value="jobPostingId" placeholder="공고 ID" allow-clear class="filter-input-sm" @press-enter="search" />
        <a-input v-model:value="applicationId" placeholder="지원번호" allow-clear class="filter-input-sm" @press-enter="search" />
        <span class="spacer" />
        <a-button @click="reset"><template #icon><ReloadOutlined /></template>초기화</a-button>
        <a-button type="primary" :loading="loading" @click="search">
          <template #icon><SearchOutlined /></template>조회
        </a-button>
      </div>
    </section>

    <p class="list-count">
      <template v-if="searched">총 <b>{{ totalElements }}</b>건</template>
      <template v-else>조회 전</template>
    </p>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      size="small"
      :custom-row="onRow"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'occurredAt'">
          {{ formatDate(asRow(record).occurredAt, 'YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'actor'">
          {{ asRow(record).actorId ?? '-' }}
          <span class="sub">{{ actorTypeLabel(asRow(record).actorType) }}</span>
        </template>
        <template v-else-if="column.key === 'actionType'">{{ actionTypeLabel(asRow(record).actionType) }}</template>
        <template v-else-if="column.key === 'target'">
          {{ targetTypeLabel(asRow(record).targetType) }}
          <span v-if="asRow(record).targetId" class="sub">{{ asRow(record).targetId }}</span>
        </template>
        <template v-else-if="column.key === 'actionResult'">
          <a-tag :color="actionResultTag(asRow(record).actionResult).color">
            {{ actionResultTag(asRow(record).actionResult).label }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'applicationId'">{{ asRow(record).applicationId ?? '-' }}</template>
        <template v-else-if="column.key === 'ipAddress'">{{ asRow(record).ipAddress ?? '-' }}</template>
      </template>
      <template #emptyText>
        <a-empty
          :description="searched ? '조회 조건에 맞는 감사 로그가 없습니다.' : '조회 조건을 지정한 뒤 조회를 누르세요.'"
        />
      </template>
    </a-table>

    <AuditLogDrawer
      v-model:open="drawerOpen"
      :activity-id="detailId"
      @open-applicant-events="(id: number) => emit('open-applicant-events', id)"
    />
  </div>
</template>

<style scoped lang="scss">
.audit-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.presets {
  margin-left: 4px;
}

.filter-select {
  width: 200px;
}

.filter-select-sm {
  width: 140px;
}

.filter-input {
  width: 160px;
}

.filter-input-sm {
  width: 110px;
}

.spacer {
  flex: 1;
}

.list-count {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.sub {
  margin-left: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 3: 커밋**

```bash
git add recruit_front/src/views/admin/log/AuditLogPanel.vue
git commit -m "feat(privacy-audit-audit): 감사 로그 조회 패널 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: 지원자 이벤트 상세 drawer

**Files:**
- Create: `recruit_front/src/views/admin/log/ApplicantEventDrawer.vue`

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { adminClientEventApi } from '@/api/admin/adminClientEventApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { ClientEventLogResponse } from '@/types/admin/clientEventLog'
import { eventTypeLabel, prettyJson, severityColor } from './logLabel'

/*
 * 지원자 이벤트 1건 상세. 목록 행 데이터를 쓰지 않고 단건 API 를 다시 부른다(404 로 없는 행을 구분한다).
 * "같은 세션 이벤트 모두 보기"는 조회 대상(지원번호)을 풀고 세션으로만 거른다 —
 * 한 세션에는 지원서 필드가 빈 행(전역 JS 오류 등)도 섞여 있어 지원번호를 유지하면 그 행들이 보이지 않는다.
 */

const props = defineProps<{ open: boolean; eventId: number | null }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'filter-session', clientSessionId: string): void
  (e: 'open-audit', applicationId: number): void
}>()

const event = ref<ClientEventLogResponse | null>(null)
const loading = ref(false)

const load = async (id: number): Promise<void> => {
  loading.value = true
  try {
    const response = await adminClientEventApi.getClientEvent(id)
    event.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '이벤트를 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.eventId] as const,
  ([open, id]) => {
    if (open && id !== null) {
      void load(id)
    }
    if (!open) {
      event.value = null
    }
  },
  { immediate: true },
)

const close = (): void => emit('update:open', false)

const goSession = (): void => {
  if (!event.value) return
  emit('filter-session', event.value.clientSessionId)
  close()
}

const goAudit = (): void => {
  if (event.value?.applicationId == null) return
  emit('open-audit', event.value.applicationId)
  close()
}
</script>

<template>
  <a-drawer
    :open="props.open"
    :width="760"
    :title="event ? `지원자 이벤트 #${event.id}` : '지원자 이벤트'"
    @close="close"
  >
    <a-spin :spinning="loading">
      <template v-if="event">
        <a-descriptions title="이벤트" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="수신시각">{{ formatDate(event.receivedAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="브라우저 발생시각">{{ event.clientOccurredAt ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="이벤트">
            {{ eventTypeLabel(event.eventType) }}
            <span class="code">{{ event.eventType }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="심각도">
            <a-tag :color="severityColor(event.severity)">{{ event.severity }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="메시지 코드">{{ event.message ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="수집 출처">{{ event.source }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="발생 위치" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="화면 코드">{{ event.pageCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="컴포넌트">{{ event.componentCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="라우트">{{ event.routePath ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="동작">{{ event.operation ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="공고 ID">{{ event.jobPostingId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원번호">{{ event.applicationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="HTTP" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="메서드 / 경로">
            <template v-if="event.apiPath">{{ event.httpMethod ?? '' }} {{ event.apiPath }}</template>
            <template v-else>-</template>
          </a-descriptions-item>
          <a-descriptions-item label="상태 코드">{{ event.httpStatus ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="오류 코드">{{ event.errorCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="오류 추적번호">{{ event.relatedCorrelationId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="수집 추적번호">{{ event.ingestCorrelationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div v-if="event.stackSummary" class="section">
          <h4 class="section-title">스택 요약 <span class="sub">해시 {{ event.stackHash ?? '-' }}</span></h4>
          <pre class="json">{{ event.stackSummary }}</pre>
        </div>

        <a-descriptions title="세션·단말" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="세션 ID">{{ event.clientSessionId }}</a-descriptions-item>
          <a-descriptions-item label="이벤트 ID">{{ event.clientEventId }}</a-descriptions-item>
          <a-descriptions-item label="브라우저">
            {{ event.browserName ?? '-' }} {{ event.browserVersion ?? '' }} / {{ event.osName ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="뷰포트 · 시간대">
            {{ event.viewport ?? '-' }} · {{ event.timezone ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="프론트 버전">{{ event.frontendVersion ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="IP 주소">{{ event.ipAddress ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="User-Agent">{{ event.userAgent ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사용자 가명키">{{ event.principalHash ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사용자 유형">{{ event.principalType ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="section">
          <h4 class="section-title">메타데이터</h4>
          <pre v-if="event.metadataJson" class="json">{{ prettyJson(event.metadataJson) }}</pre>
          <p v-else class="empty-text">없음</p>
        </div>
      </template>
    </a-spin>

    <template #footer>
      <div class="footer">
        <div class="actions">
          <a-button :disabled="!event" @click="goSession">같은 세션 이벤트 모두 보기</a-button>
          <a-button v-if="event?.applicationId != null" @click="goAudit">이 지원서 관련 감사 로그 보기</a-button>
        </div>
        <a-button @click="close">닫기</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.section {
  margin-bottom: 18px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
}

.code {
  margin-left: 6px;
  font-family: monospace;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
}

.json {
  margin: 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
}

.empty-text {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
```

- [ ] **Step 2: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 3: 커밋**

```bash
git add recruit_front/src/views/admin/log/ApplicantEventDrawer.vue
git commit -m "feat(client-event-log): 지원자 이벤트 상세 drawer 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: 지원자 찾기 패널

**Files:**
- Create: `recruit_front/src/views/admin/log/ApplicantFinderPanel.vue`

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'

import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminApplicationSummaryResponse } from '@/types/admin/application'

/*
 * 지원자 찾기. 두 로그 테이블에는 이름·휴대폰이 없어서 지원번호로 바꿔 주는 단계가 필요하다.
 * 응답(AdminApplicationSummaryResponse)에는 휴대폰이 없다(검색 조건으로만 쓴다) — 동명이인은 생년월일로 가린다.
 * 파기된 지원자는 이름 스냅샷이 익명화돼 검색되지 않으므로 "지원번호 직접 입력"을 항상 열어 둔다.
 * 관리자 화면이므로 이름·생년월일은 마스킹하지 않는다.
 */

const emit = defineEmits<{ (e: 'select', applicationId: number, applicantName: string | null): void }>()

const STATUS_LABEL: Record<AdminApplicationSummaryResponse['status'], string> = {
  DRAFT: '작성중',
  SUBMITTED: '제출완료',
  WITHDRAWN: '철회',
}

const name = ref('')
const phoneNumber = ref('')
const directApplicationId = ref('')
const rows = ref<AdminApplicationSummaryResponse[]>([])
const loading = ref(false)
const searched = ref(false)

const columns = [
  { title: '지원번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'applicantNameSnapshot', width: 110 },
  { title: '생년월일 (나이)', key: 'birthDate', width: 150 },
  { title: '공고 / 모집분야', key: 'posting', ellipsis: true },
  { title: '상태', key: 'status', width: 100 },
]

const asRow = (record: unknown): AdminApplicationSummaryResponse => record as AdminApplicationSummaryResponse

const search = async (): Promise<void> => {
  const trimmedName = name.value.trim()
  const trimmedPhone = phoneNumber.value.trim()
  if (!trimmedName && !trimmedPhone) {
    message.warning('이름 또는 휴대폰 번호를 입력하세요.')
    return
  }
  loading.value = true
  try {
    const response = await adminApplicationApi.searchApplicants(
      trimmedName || undefined,
      trimmedPhone || undefined,
    )
    rows.value = response.data.data.content
    searched.value = true
  } catch (error) {
    message.error(getApiErrorMessage(error, '지원자를 조회하지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const select = (row: AdminApplicationSummaryResponse): void => {
  emit('select', row.applicationId, row.applicantNameSnapshot)
}

const onRow = (record: unknown) => ({ onClick: () => select(asRow(record)) })

const applyDirectId = (): void => {
  const trimmed = directApplicationId.value.trim()
  if (!/^[0-9]+$/.test(trimmed)) {
    message.warning('지원번호는 숫자로 입력하세요.')
    return
  }
  emit('select', Number(trimmed), null)
}
</script>

<template>
  <div class="finder">
    <div class="search-bar">
      <a-input v-model:value="name" placeholder="지원자 이름" allow-clear class="search-input" @press-enter="search" />
      <a-input v-model:value="phoneNumber" placeholder="휴대폰 번호(숫자 일부)" allow-clear class="search-input" @press-enter="search" />
      <a-button type="primary" :loading="loading" @click="search">
        <template #icon><SearchOutlined /></template>찾기
      </a-button>
    </div>

    <a-table
      v-if="searched"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="applicationId"
      size="small"
      :custom-row="onRow"
      class="result-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'applicationId'">{{ asRow(record).applicationId }}</template>
        <template v-else-if="column.key === 'applicantNameSnapshot'">{{ asRow(record).applicantNameSnapshot }}</template>
        <template v-else-if="column.key === 'birthDate'">
          {{ asRow(record).birthDate ?? '-' }}
          <span v-if="asRow(record).age != null" class="sub">({{ asRow(record).age }}세)</span>
        </template>
        <template v-else-if="column.key === 'posting'">
          {{ asRow(record).jobPostingTitleSnapshot }} / {{ asRow(record).jobPositionNameSnapshot }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag>{{ STATUS_LABEL[asRow(record).status] }}</a-tag>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="일치하는 지원자가 없습니다. 파기된 지원자는 검색되지 않습니다." />
      </template>
    </a-table>

    <a-divider class="divider">또는</a-divider>

    <div class="direct-bar">
      <a-input
        v-model:value="directApplicationId"
        placeholder="지원번호 직접 입력 (예: 1042)"
        allow-clear
        class="search-input"
        @press-enter="applyDirectId"
      />
      <a-button @click="applyDirectId">적용</a-button>
      <span class="hint">파기된 지원자는 이름·휴대폰으로 찾을 수 없습니다. 지원번호를 알고 있다면 여기에 입력하세요.</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.finder {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-bar,
.direct-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 220px;
}

.divider {
  margin: 4px 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.sub {
  margin-left: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.result-table {
  :deep(.ant-table-tbody > tr) {
    cursor: pointer;
  }
}
</style>
```

- [ ] **Step 2: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 3: 커밋**

```bash
git add recruit_front/src/views/admin/log/ApplicantFinderPanel.vue
git commit -m "feat(client-event-log): 로그 조회 지원자 찾기 패널 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: 지원자 이벤트 패널

**Files:**
- Create: `recruit_front/src/views/admin/log/ApplicantEventPanel.vue`

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { message } from 'ant-design-vue'
import { AimOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { adminClientEventApi } from '@/api/admin/adminClientEventApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { ClientEventLogQuery, ClientEventLogResponse } from '@/types/admin/clientEventLog'
import type { ClientEventSeverity, ClientEventType } from '@/types/clientEvent'
import ApplicantEventDrawer from './ApplicantEventDrawer.vue'
import ApplicantFinderPanel from './ApplicantFinderPanel.vue'
import { EVENT_TYPE_OPTIONS, SEVERITY_OPTIONS, eventTypeLabel, severityColor } from './logLabel'
import { RANGE_PRESETS, type DateRange, presetRange, rangeError, toDateTimeRange } from './logQuery'

/*
 * 지원자 이벤트 탭. 지원자 찾기로 지원번호를 고정한 뒤 그 지원자의 브라우저 오류를 본다.
 * 세션 ID·오류 추적번호만으로도 조회할 수 있다(지원번호 없이).
 * 진입 시 자동 조회하지 않는다.
 */

const PAGE_SIZE = 20
const DEFAULT_RANGE_DAYS = 7

const emit = defineEmits<{ (e: 'open-audit', applicationId: number): void }>()

const range = ref<DateRange>(presetRange(DEFAULT_RANGE_DAYS, new Date()))
const eventType = ref<ClientEventType | undefined>(undefined)
const severity = ref<ClientEventSeverity | undefined>(undefined)
const clientSessionId = ref('')
const relatedCorrelationId = ref('')

const targetApplicationId = ref<number | null>(null)
const targetApplicantName = ref<string | null>(null)
/* a-collapse 는 열린 패널 key 배열을 받는다. 빈 배열이면 접힌 상태. */
const finderKeys = ref<string[]>(['finder'])

const rows = ref<ClientEventLogResponse[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)
const searched = ref(false)
const appliedQuery = ref<Omit<ClientEventLogQuery, 'page' | 'size'>>({})

const columns = [
  { title: '수신시각', key: 'receivedAt', width: 160 },
  { title: '심각도', key: 'severity', width: 90 },
  { title: '이벤트', key: 'eventType', width: 150 },
  { title: '메시지 코드', key: 'message', width: 200 },
  { title: 'HTTP', key: 'httpStatus', width: 70 },
  { title: '화면 / API', key: 'location', ellipsis: true },
  { title: '지원번호', key: 'applicationId', width: 100 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const asRow = (record: unknown): ClientEventLogResponse => record as ClientEventLogResponse

const locationText = (row: ClientEventLogResponse): string =>
  row.pageCode ?? row.apiPath ?? row.routePath ?? '-'

const buildQuery = (): Omit<ClientEventLogQuery, 'page' | 'size'> => ({
  ...toDateTimeRange(range.value),
  eventType: eventType.value,
  severity: severity.value,
  applicationId: targetApplicationId.value ?? undefined,
  clientSessionId: clientSessionId.value.trim() || undefined,
  relatedCorrelationId: relatedCorrelationId.value.trim() || undefined,
})

let listRequest = 0

const loadList = async (): Promise<void> => {
  const request = ++listRequest
  loading.value = true
  try {
    const response = await adminClientEventApi.getClientEvents({
      ...appliedQuery.value,
      page: page.value,
      size: PAGE_SIZE,
    })
    if (request !== listRequest) return
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
    searched.value = true
  } catch (error) {
    if (request !== listRequest) return
    message.error(getApiErrorMessage(error, '이벤트를 불러오지 못했습니다.'))
  } finally {
    if (request === listRequest) {
      loading.value = false
    }
  }
}

const search = (): void => {
  const error = rangeError(range.value[0], range.value[1])
  if (error !== null) {
    message.warning(error)
    return
  }
  appliedQuery.value = buildQuery()
  page.value = 0
  void loadList()
}

const reset = (): void => {
  range.value = presetRange(DEFAULT_RANGE_DAYS, new Date())
  eventType.value = undefined
  severity.value = undefined
  clientSessionId.value = ''
  relatedCorrelationId.value = ''
  targetApplicationId.value = null
  targetApplicantName.value = null
  finderKeys.value = ['finder']
  rows.value = []
  totalElements.value = 0
  page.value = 0
  searched.value = false
}

const applyPreset = (days: number): void => {
  range.value = presetRange(days, new Date())
}

const onRangeChange = (_: unknown, dateStrings: [string, string]): void => {
  if (dateStrings[0] && dateStrings[1]) {
    range.value = dateStrings
  }
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const onSelectApplicant = async (applicationId: number, applicantName: string | null): Promise<void> => {
  targetApplicationId.value = applicationId
  targetApplicantName.value = applicantName
  finderKeys.value = []
  await nextTick()
  search()
}

const clearTarget = (): void => {
  targetApplicationId.value = null
  targetApplicantName.value = null
  finderKeys.value = ['finder']
  rows.value = []
  totalElements.value = 0
  page.value = 0
  searched.value = false
}

const drawerOpen = ref(false)
const detailId = ref<number | null>(null)

const openDetail = (row: ClientEventLogResponse): void => {
  detailId.value = row.id
  drawerOpen.value = true
}

const onRow = (record: unknown) => ({ onClick: () => openDetail(asRow(record)) })

/*
 * 세션 단위 조회. 조회 대상(지원번호)을 일부러 푼다 — 한 세션에는 지원서 필드가 빈 행도 있어서
 * 지원번호를 함께 걸면 그 행들이 빠진다.
 */
const filterBySession = async (sessionId: string): Promise<void> => {
  targetApplicationId.value = null
  targetApplicantName.value = null
  eventType.value = undefined
  severity.value = undefined
  relatedCorrelationId.value = ''
  clientSessionId.value = sessionId
  await nextTick()
  search()
}

/** 감사 로그 탭에서 넘어올 때: 지원번호만 걸고 바로 조회한다. */
const applyApplicationId = async (applicationId: number): Promise<void> => {
  reset()
  await nextTick()
  await onSelectApplicant(applicationId, null)
}

defineExpose({ applyApplicationId })
</script>

<template>
  <div class="applicant-event-panel">
    <a-collapse v-model:activeKey="finderKeys" :bordered="false" class="finder-collapse">
      <a-collapse-panel key="finder" header="지원자 찾기 — 이름·휴대폰으로 검색해 지원번호를 찾습니다">
        <ApplicantFinderPanel @select="onSelectApplicant" />
      </a-collapse-panel>
    </a-collapse>

    <a-alert v-if="targetApplicationId !== null" type="success" class="target-bar">
      <template #message>
        <span class="target-text">
          <AimOutlined />
          조회 대상 <b>지원번호 {{ targetApplicationId }}</b>
          <template v-if="targetApplicantName"> · {{ targetApplicantName }}</template>
          <span class="sub">이 지원자의 브라우저 이벤트만 표시합니다</span>
        </span>
      </template>
      <template #action>
        <a-button size="small" type="text" @click="clearTarget">해제</a-button>
      </template>
    </a-alert>

    <section class="filters">
      <div class="filter-row">
        <label class="filter-label">기간 (시작일·종료일 포함 최대 90일)</label>
        <a-range-picker
          :value="range"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          :placeholder="['시작일', '종료일']"
          @change="onRangeChange"
        />
        <a-space :size="4" class="presets">
          <a-button v-for="days in RANGE_PRESETS" :key="days" size="small" @click="applyPreset(days)">
            {{ days }}일
          </a-button>
        </a-space>
      </div>

      <div class="filter-row">
        <a-select
          v-model:value="eventType"
          :options="EVENT_TYPE_OPTIONS"
          placeholder="전체 이벤트"
          allow-clear
          class="filter-select"
        />
        <a-select
          v-model:value="severity"
          :options="SEVERITY_OPTIONS"
          placeholder="전체 심각도"
          allow-clear
          class="filter-select-sm"
        />
        <a-input v-model:value="clientSessionId" placeholder="세션 ID(완전일치)" allow-clear class="filter-input" @press-enter="search" />
        <a-input v-model:value="relatedCorrelationId" placeholder="오류 추적번호(완전일치)" allow-clear class="filter-input" @press-enter="search" />
        <span class="spacer" />
        <a-button @click="reset"><template #icon><ReloadOutlined /></template>초기화</a-button>
        <a-button type="primary" :loading="loading" @click="search">
          <template #icon><SearchOutlined /></template>조회
        </a-button>
      </div>
    </section>

    <p class="list-count">
      <template v-if="searched">
        총 <b>{{ totalElements }}</b>건
        <template v-if="targetApplicationId !== null"> · 지원번호 {{ targetApplicationId }} 필터 적용 중</template>
      </template>
      <template v-else>조회 전</template>
    </p>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      size="small"
      :custom-row="onRow"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'receivedAt'">
          {{ formatDate(asRow(record).receivedAt, 'YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-tag :color="severityColor(asRow(record).severity)">{{ asRow(record).severity }}</a-tag>
        </template>
        <template v-else-if="column.key === 'eventType'">{{ eventTypeLabel(asRow(record).eventType) }}</template>
        <template v-else-if="column.key === 'message'">{{ asRow(record).message ?? '-' }}</template>
        <template v-else-if="column.key === 'httpStatus'">{{ asRow(record).httpStatus ?? '-' }}</template>
        <template v-else-if="column.key === 'location'">{{ locationText(asRow(record)) }}</template>
        <template v-else-if="column.key === 'applicationId'">{{ asRow(record).applicationId ?? '-' }}</template>
      </template>
      <template #emptyText>
        <a-empty
          :description="searched ? '조회 조건에 맞는 이벤트가 없습니다.' : '지원자를 먼저 선택하세요. 세션 ID·오류 추적번호로 바로 조회할 수도 있습니다.'"
        />
      </template>
    </a-table>

    <ApplicantEventDrawer
      v-model:open="drawerOpen"
      :event-id="detailId"
      @filter-session="filterBySession"
      @open-audit="(id: number) => emit('open-audit', id)"
    />
  </div>
</template>

<style scoped lang="scss">
.applicant-event-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.finder-collapse {
  background: var(--app-surface-muted, #fafafa);
}

.target-bar {
  align-items: center;
}

.target-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.presets {
  margin-left: 4px;
}

.filter-select {
  width: 200px;
}

.filter-select-sm {
  width: 140px;
}

.filter-input {
  width: 220px;
}

.spacer {
  flex: 1;
}

.list-count {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.sub {
  margin-left: 8px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: 타입 검사**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

- [ ] **Step 3: 커밋**

```bash
git add recruit_front/src/views/admin/log/ApplicantEventPanel.vue
git commit -m "feat(client-event-log): 지원자 이벤트 조회 패널 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: 껍데기 뷰 + 라우트

**Files:**
- Create: `recruit_front/src/views/admin/log/AdminLogView.vue`
- Modify: `recruit_front/src/routes/adminRoutes.ts`

- [ ] **Step 1: `AdminLogView.vue` 작성**

```vue
<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/authStore'
import ApplicantEventPanel from './ApplicantEventPanel.vue'
import AuditLogPanel from './AuditLogPanel.vue'

/*
 * 로그 조회 화면. 탭 2개(지원자 이벤트 / 감사 로그).
 *
 * 두 조회 API 는 ROLE_RECRUIT_ADMIN·ROLE_PRIVACY_ADMIN 만 허용한다. ROLE_ADMIN 만 가진 계정은
 * 라우트 가드(ADMIN_ROLES)는 통과하지만 조회에서 403 을 받고, 공통 인터셉터가 /403 으로 보내 버린다.
 * 그래서 권한이 없으면 탭 자체를 렌더링하지 않고 안내만 띄운다(AdminRetentionView 와 같은 방식).
 *
 * IP·User-Agent 원문은 ROLE_PRIVACY_ADMIN 만 본다. 그 외에는 서버가 "***" 로 바꿔 보내므로
 * 화면은 마스킹 판정을 하지 않고 안내 배너만 띄운다.
 */

const authStore = useAuthStore()
const canQuery = computed(
  () => authStore.roles.includes('ROLE_RECRUIT_ADMIN') || authStore.roles.includes('ROLE_PRIVACY_ADMIN'),
)
const canSeeSensitive = computed(() => authStore.roles.includes('ROLE_PRIVACY_ADMIN'))

const activeTab = ref<'client' | 'audit'>('client')

const applicantPanelRef = ref<InstanceType<typeof ApplicantEventPanel> | null>(null)
const auditPanelRef = ref<InstanceType<typeof AuditLogPanel> | null>(null)

/*
 * a-tab-pane 은 처음 활성화되기 전까지 렌더링하지 않으므로(lazy), 탭을 바꾼 뒤 nextTick 으로
 * 패널이 마운트되기를 기다린 다음 노출 메서드를 부른다.
 */
const openApplicantEvents = async (applicationId: number): Promise<void> => {
  activeTab.value = 'client'
  await nextTick()
  void applicantPanelRef.value?.applyApplicationId(applicationId)
}

const openAudit = async (applicationId: number): Promise<void> => {
  activeTab.value = 'audit'
  await nextTick()
  void auditPanelRef.value?.applyApplicationId(applicationId)
}

/*
 * 딥링크: /admin/logs?tab=audit&applicationId=1042
 * 다른 화면(지원현황 등)에서 넘어오는 입구다. 진입 시 한 번 읽기만 하고, 이후 필터 변경을
 * URL 에 되쓰지는 않는다(되쓰려면 필터 전체를 쿼리로 직렬화해야 해서 얻는 것보다 비싸다).
 */
const route = useRoute()

onMounted(() => {
  if (!canQuery.value) return
  if (route.query.tab === 'audit') {
    activeTab.value = 'audit'
  }
  const rawApplicationId = route.query.applicationId
  const applicationId = Number(Array.isArray(rawApplicationId) ? rawApplicationId[0] : rawApplicationId)
  if (!Number.isInteger(applicationId) || applicationId <= 0) return
  if (activeTab.value === 'audit') {
    void openAudit(applicationId)
  } else {
    void openApplicantEvents(applicationId)
  }
})
</script>

<template>
  <div class="log-view">
    <header class="page-header">
      <h1 class="page-title">로그 조회</h1>
      <p class="page-desc">
        <b>지원자 이벤트</b>는 지원자 브라우저에서 발생한 오류를, <b>감사 로그</b>는 관리자·시스템의 서버 행위
        증적을 조회합니다. 두 로그 모두 지원자 이름·연락처를 저장하지 않습니다.
      </p>
    </header>

    <a-alert
      v-if="!canQuery"
      type="info"
      show-icon
      message="로그 조회 권한이 없습니다"
      description="로그 조회에는 채용 관리자 권한(ROLE_RECRUIT_ADMIN) 또는 개인정보 관리자 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
    />

    <template v-else>
      <a-alert
        v-if="!canSeeSensitive"
        type="warning"
        show-icon
        class="mask-notice"
        message="IP 주소·브라우저 정보(User-Agent)는 ***로 가려져 있습니다"
        description="원문 확인에는 개인정보 관리자 권한(ROLE_PRIVACY_ADMIN)이 필요합니다."
      />

      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="client" tab="지원자 이벤트">
          <ApplicantEventPanel ref="applicantPanelRef" @open-audit="openAudit" />
        </a-tab-pane>
        <a-tab-pane key="audit" tab="감사 로그">
          <AuditLogPanel ref="auditPanelRef" @open-applicant-events="openApplicantEvents" />
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>

<style scoped lang="scss">
.log-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header {
  margin-bottom: 0;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary);
  max-width: 900px;
}

.mask-notice {
  margin-bottom: 0;
}
</style>
```

- [ ] **Step 2: 라우트 등록**

`recruit_front/src/routes/adminRoutes.ts`의 `children` 배열에서 `role-mappings` 항목 **바로 아래**에 넣는다:

```ts
      {
        path: 'logs',
        name: 'AdminLogInquiry',
        component: () => import('@/views/admin/log/AdminLogView.vue'),
      },
```

`meta`는 부모(`/admin`)에서 병합되므로 따로 쓰지 않는다.

- [ ] **Step 3: 타입 검사 + 전체 단위 테스트**

Run: `cd recruit_front && npm run type-check`
Expected: 오류 0건

Run: `cd recruit_front && npx vitest run`
Expected: 기존 테스트 + 새 테스트 모두 PASS

- [ ] **Step 4: 커밋**

```bash
git add recruit_front/src/views/admin/log/AdminLogView.vue recruit_front/src/routes/adminRoutes.ts
git commit -m "feat(client-event-log): 로그 조회 화면·라우트 추가

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 11: 화면 동작 확인

개발 서버를 띄우고 직접 확인한다. 자동 테스트로 못 잡는 항목만 본다.

- [ ] **Step 1: 개발 서버 실행**

`recruit_front/` 에서 `npm run dev`. 백엔드가 떠 있어야 한다(`recruit_back/recruit_backend/`에서 `AES_SECRET_KEY` 환경변수와 함께 `gradlew bootRun`).

- [ ] **Step 2: 확인 항목**

`http://localhost:5173/admin/logs`(포트는 Vite 출력 확인)에서:

- [ ] 진입 직후 두 탭 모두 표가 비어 있고 `조회 전`이 보인다.
- [ ] 감사 탭에서 `조회`를 누르면 최근 30일 목록이 나온다.
- [ ] 감사 탭 기간을 91일치로 벌리고 `조회`를 누르면 서버 400이 아니라 화면 경고(`조회 기간은 시작일·종료일 포함 90일까지입니다.`)가 뜬다.
- [ ] 90일 프리셋으로 조회하면 400이 나지 않는다.
- [ ] 지원자 찾기에서 이름으로 검색하면 결과가 나오고, 행을 누르면 조회 대상 배너가 뜨며 목록이 채워진다.
- [ ] 지원번호 직접 입력으로도 같은 결과가 나온다.
- [ ] 이벤트 상세에서 `같은 세션 이벤트 모두 보기`를 누르면 조회 대상 배너가 사라지고 세션 기준 목록이 나온다.
- [ ] 이벤트 상세에서 `이 지원서 관련 감사 로그 보기`를 누르면 감사 탭으로 이동하고 지원번호 필터가 채워진 채 조회돼 있다.
- [ ] 감사 상세에서 `이 지원서의 지원자 이벤트 보기`를 누르면 반대로 이동한다. `applicationId`가 없는 행에는 버튼 대신 안내 문구가 보인다.
- [ ] `ROLE_RECRUIT_ADMIN` 계정에서 IP·User-Agent가 `***`로 보이고 상단에 안내 배너가 있다.
- [ ] 탭을 오가도 각 탭의 필터와 조회 결과가 유지된다.
- [ ] `/admin/logs?tab=audit&applicationId=<실제 지원번호>` 로 바로 들어가면 감사 탭이 열리고 그 지원번호로 조회돼 있다.

- [ ] **Step 3: 어긋나는 항목이 있으면 고치고 다시 확인한다**

고친 뒤 `npm run type-check`와 `npx vitest run`을 다시 돌린다.

---

### Task 12: 문서 갱신

코드만 바꾸고 카드를 안 고치면 미완료다(`AGENTS.md` 6절).

**Files:**
- Modify: `docs/domains/client-event-log.md`
- Modify: `docs/domains/privacy-audit-audit.md`
- Modify: `docs/domains/_index.md`
- Modify: `docs/domains/admin-application.md`

- [ ] **Step 1: `client-event-log.md`**

- `## 파일 지도` 프론트 절에 추가: `AdminLogView.vue` · `ApplicantEventPanel.vue` · `ApplicantFinderPanel.vue` · `ApplicantEventDrawer.vue` · `logLabel.ts` · `logQuery.ts` · `adminClientEventApi.ts` · `types/admin/clientEventLog.ts` · `__tests__/logQuery.spec.ts` · `__tests__/logLabel.spec.ts`.
- `## 요약`의 "관리자 조회·cleanup API는 **FE 화면 없음**(운영자 직접 호출)" → 조회는 로그 조회 화면이 쓰고, cleanup만 화면 없음으로 고친다.
- `## 요약`의 "**소유 화면 없음.**" 제거.
- `## API 계약`의 `GET /admin/client-events`·`/{id}` 엔드포인트 상세에서 **FE 미사용** 표기를 지우고 FE 매핑(`adminClientEventApi.getClientEvents()`/`getClientEvent()`)을 적는다.

- [ ] **Step 2: `privacy-audit-audit.md`**

- `## 파일 지도` 프론트 절의 "없음(...)"을 신규 파일(`AuditLogPanel.vue` · `AuditLogDrawer.vue` · `adminAuditApi.ts` · `types/admin/auditLog.ts`)로 바꾸고, 껍데기·공용 유틸은 [client-event-log](../../domains/client-event-log.md) 소유임을 링크로 적는다.
- `## API 계약`의 "전 엔드포인트 **FE 미사용**" 문구를 FE 매핑으로 바꾼다.
- `## 파일 지도` enum 행의 개수 표기를 코드에 맞게 고친다: `AuditActionType`은 20 → **21**(`PURGE_FORCED` 포함), `AuditTargetType`은 10 → **11**(`RETENTION_SCHEDULE` 포함).
- `## 함정·결정`에 `ROLE_PRIVACY_ADMIN` 전용 계정이 `ADMIN_ROLES` 가드에 막혀 화면에 못 들어온다는 항목을 추가한다.

- [ ] **Step 3: `_index.md`**

- 라우트 역색인에 한 줄 추가: `` | `AdminLogInquiry` | `/admin/logs` | client-event-log | ``
- 카드 목록의 client-event-log 설명을 "프론트 이벤트·오류 로그 수집"에서 "수집·조회 화면"으로 고친다.

- [ ] **Step 4: `admin-application.md`**

`GET /admin/applications` 관련 "FE는 공고 경로만 쓴다"는 문장에 로그 조회 화면의 `adminApplicationApi.searchApplicants()` 사용처를 더한다.

- [ ] **Step 5: 문서 점검**

Run: `node tools/check-docs.mjs`
Expected: 오류 0건

- [ ] **Step 6: 커밋**

```bash
git add docs/domains/client-event-log.md docs/domains/privacy-audit-audit.md docs/domains/_index.md docs/domains/admin-application.md
git commit -m "docs(client-event-log): 로그 조회 화면 카드 갱신

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## 완료 후 남는 작업 (이 계획 밖)

1. **사이드바 메뉴 등록** — 관리자 사이드바는 DB `menu` 테이블에서 렌더링한다. 배포 후 `/admin/menus`에서 `ADMIN` 사이트에 소메뉴(`path=/admin/logs`)를 만들어야 사이드바에 뜬다. 메뉴가 없어도 URL 직접 입력으로는 동작한다.
2. **`ROLE_PRIVACY_ADMIN` 라우트 가드** — `ADMIN_ROLES`를 고칠지 별도 판단이 필요하다(로그인 직후 이동 분기와 공유하는 값).
3. **지원현황 화면의 "로그 보기" 링크** — 받는 쪽(`?tab=`·`?applicationId=` 파싱)은 Task 10 에 포함돼 있다. 링크를 거는 것은 [admin-application](../../domains/admin-application.md) 화면 변경이라 별도 슬라이스다.
