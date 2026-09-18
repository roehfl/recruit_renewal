# 지원서 폼 섹션 (공백기간·자기소개) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ApplicationFormView`의 placeholder인 `GAP_PERIOD`(공백기간)·`QUESTION_ANSWER`(자기소개)를 실제 입력 컴포넌트로 교체한다.

**Architecture:** 앞선 어학/수상/자격증과 동일한 섹션 컴포넌트 패턴(view + api + types, `defineExpose({ saveDraft, validateBeforeSubmit })`). 공백기간은 카드형 replace-list(수상/자격증과 동일 골격, "해당 사항 없음" 체크박스는 주석 처리). 자기소개는 **질문 응답형** — `GET /questions`로 공고 질문을 받아 답변 입력, `POST /answers`로 `{questionId, answerText}` 전송(추가/삭제·NA 없음). 백엔드 변경 없음, 새 라우트 없음.

**Tech Stack:** Vue 3.5 `<script setup lang="ts">`, TypeScript, ant-design-vue 4.2.6, `@ant-design/icons-vue`, axios(`apiClient`). 설계서: `docs/superpowers/specs/2026-06-24-application-sections-gap-period-question-answer-design.md`.

**검증 방식:** 각 태스크 게이트는 `npm run type-check`(필요 시 `npm run build`), `recruit_front/`에서 실행. 단위 테스트는 강제하지 않음(AGENTS.md). 리포지토리에 `noUncheckedIndexedAccess`가 켜져 있어 `items[i]` 접근에는 `if (!item) continue` 가드 필요(아래 코드에 포함).

**Git 규칙:** CLAUDE.md §6 — 명시 승인 없이 commit/push 금지. 각 태스크 끝의 commit 단계는 사용자가 승인한 경우에만 `recruit_front/`에서 실행.

**작업 디렉토리:** 모든 경로는 `recruit_front/` 기준, `npm`은 `recruit_front/`에서 실행.

---

## File Structure

신규(6):
- `src/types/application/sections/gapPeriod.ts` — 공백기간 타입
- `src/types/application/sections/questionAnswer.ts` — 자기소개(질문/답변) 타입
- `src/api/application/sections/gapPeriodApi.ts` — 공백기간 GET/POST
- `src/api/application/sections/questionAnswerApi.ts` — 질문 GET / 답변 POST
- `src/views/applicant/application/sections/GapPeriodSection.vue` — 공백기간(카드형)
- `src/views/applicant/application/sections/QuestionAnswerSection.vue` — 자기소개(질문 응답형)

수정(2):
- `src/views/applicant/ApplicationFormView.vue` — import 2 + `sectionComponentMap` 2줄 교체
- `api-contract.md`(recruit 루트) — 공백기간·자기소개 섹션 추가(🟢)

---

## Task 1: 공백기간(GapPeriod) 섹션

**Files:**
- Create: `src/types/application/sections/gapPeriod.ts`
- Create: `src/api/application/sections/gapPeriodApi.ts`
- Create: `src/views/applicant/application/sections/GapPeriodSection.vue`
- Modify: `src/views/applicant/ApplicationFormView.vue`

계약: `GET/POST /applications/{id}/gap-periods`, body `{ gapPeriods: [...] }`. 항목 필수: `startDate`, `endDate`, `gapType`, `reason`. `gapType` enum `EDUCATION`/`CAREER`/`OTHER`(라벨 학업/경력/기타, 하드코딩). "해당 사항 없음" 체크박스는 앞 3종과 동일하게 **주석 처리**.

- [ ] **Step 1: 타입 파일 생성**

`src/types/application/sections/gapPeriod.ts`:

```ts
export type GapType = 'EDUCATION' | 'CAREER' | 'OTHER'

// 폼 입력용 항목(gapType은 미선택 '' 허용)
export interface GapPeriodItem {
  gapPeriodId?: number
  startDate: string
  endDate: string
  gapType: GapType | ''
  reason: string
  description?: string
}

export interface GapPeriodRequestItem {
  startDate: string
  endDate: string
  gapType: GapType
  reason: string
  description?: string
  sortOrder: number
}

export interface GapPeriodReplaceRequest {
  gapPeriods: GapPeriodRequestItem[]
}

export interface GapPeriodResponse {
  gapPeriodId: number
  startDate: string
  endDate: string
  gapType: GapType
  reason: string
  description?: string
  sortOrder: number
}
```

- [ ] **Step 2: API 모듈 생성**

`src/api/application/sections/gapPeriodApi.ts`:

```ts
import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { GapPeriodReplaceRequest, GapPeriodResponse } from '@/types/application/sections/gapPeriod'

export const gapPeriodApi = {
  getApplicationsGapPeriods(applicationId: number) {
    return apiClient.get<ApiResponse<GapPeriodResponse[]>>(`applications/${applicationId}/gap-periods`)
  },

  replaceApplicationsGapPeriods(applicationId: number, payload: GapPeriodReplaceRequest) {
    return apiClient.post<ApiResponse<GapPeriodResponse[]>>(`applications/${applicationId}/gap-periods`, payload)
  },
}
```

- [ ] **Step 3: 컴포넌트 생성**

`src/views/applicant/application/sections/GapPeriodSection.vue`:

```vue
<template>
  <div class="section-body">
    <!--
      '해당 사항 없음' 비활성화: 없음 상태가 백엔드에 저장되지 않아(빈 배열=미입력과 구분 불가)
      새로고침 시 체크가 풀리는 문제로 주석 처리. 백엔드에 notApplicable 영속화가 생기면 되살린다.
    <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      공백기간 없음 (해당 사항 없음)
    </a-checkbox>
    -->

    <div v-if="notApplicable" class="na-box">공백기간 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">공백 {{ index + 1 }}</span>
            <button type="button" class="remove-btn" @click="removeItem(index)">
              <DeleteOutlined /> 삭제
            </button>
          </div>

          <table class="field-table">
            <colgroup>
              <col style="width: 14%" /><col style="width: 36%" />
              <col style="width: 14%" /><col style="width: 36%" />
            </colgroup>
            <tbody>
              <tr>
                <th>시작일<em> *</em></th>
                <td><a-date-picker v-model:value="item.startDate" value-format="YYYY-MM-DD" /></td>
                <th>종료일<em> *</em></th>
                <td><a-date-picker v-model:value="item.endDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>구분<em> *</em></th>
                <td><a-select v-model:value="item.gapType" :options="gapTypeOptions" placeholder="선택" /></td>
                <th>사유<em> *</em></th>
                <td><a-input v-model:value="item.reason" placeholder="예) 어학연수 / 자격증 준비" /></td>
              </tr>
              <tr>
                <th class="th-top">상세설명</th>
                <td colspan="3">
                  <a-textarea
                    v-model:value="item.description"
                    :maxlength="2000"
                    :rows="2"
                    show-count
                    placeholder="공백기간 동안의 활동을 간단히 작성하세요. (선택)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 공백기간이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 공백기간을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 공백기간 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { gapPeriodApi } from '@/api/application/sections/gapPeriodApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  GapType,
  GapPeriodItem,
  GapPeriodResponse,
  GapPeriodReplaceRequest,
} from '@/types/application/sections/gapPeriod'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<GapPeriodItem[]>([])

const gapTypeOptions: { value: GapType; label: string }[] = [
  { value: 'EDUCATION', label: '학업' },
  { value: 'CAREER', label: '경력' },
  { value: 'OTHER', label: '기타' },
]

function createEmptyItem(): GapPeriodItem {
  return {
    startDate: '',
    endDate: '',
    gapType: '',
    reason: '',
    description: '',
  }
}

function setItems(list: GapPeriodResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      gapPeriodId: row.gapPeriodId,
      startDate: row.startDate ?? '',
      endDate: row.endDate ?? '',
      gapType: row.gapType,
      reason: row.reason ?? '',
      description: row.description ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): GapPeriodReplaceRequest {
  if (notApplicable.value) return { gapPeriods: [] }
  return {
    gapPeriods: items.map((item, index) => ({
      startDate: item.startDate,
      endDate: item.endDate,
      gapType: item.gapType as GapType,
      reason: item.reason,
      description: item.description || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("공백기간을 추가하거나 '공백기간 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.startDate || !item.endDate || !item.gapType || !item.reason) {
      message.warning(`공백 ${i + 1}: 시작일, 종료일, 구분, 사유는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadGapPeriods() {
  loading.value = true
  try {
    const result = await gapPeriodApi.getApplicationsGapPeriods(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await gapPeriodApi.replaceApplicationsGapPeriods(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_GAP_PERIOD',
      operation: 'SAVE_DRAFT_GAP_PERIOD',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '공백기간 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadGapPeriods()
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}
.na-checkbox {
  margin-bottom: 18px;
  font-weight: 600;
  color: #1f2937;
}
.na-checkbox :deep(.ant-checkbox-checked .ant-checkbox-inner) {
  background-color: #0f4726;
  border-color: #0f4726;
}
.na-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
  color: #9ca3af;
  font-size: 14px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.item-card {
  border: 1px solid #eef1ee;
  border-radius: 12px;
  background: #fff;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.item-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.num-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 6px;
  background: #f4f8f0;
  color: #536d2f;
  font-size: 13px;
  font-weight: 800;
}
.remove-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #ff4d4f;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  padding: 4px 6px;
  border-radius: 6px;
}
.remove-btn:hover {
  background: #fff2f0;
}
.field-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.field-table th,
.field-table td {
  border: 1px solid #f0f0f0;
  padding: 12px;
  font-size: 14px;
}
.field-table th {
  background: #fafafa;
  text-align: left;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
}
.field-table td {
  vertical-align: top;
}
.th-top {
  vertical-align: top;
}
.empty-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
}
.empty-title {
  margin: 0 0 4px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
}
.empty-desc {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
}
.add-btn {
  margin-top: 14px;
  width: 100%;
  height: 42px;
  border: 1px dashed #b7c4a8;
  border-radius: 10px;
  background: #f8faf6;
  color: #536d2f;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.add-btn:hover {
  border-color: #6f8f3d;
  background: #f1f6ea;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-picker),
:deep(.ant-select) {
  width: 100%;
}
</style>
```

- [ ] **Step 4: ApplicationFormView에 와이어링**

import 블록(현재 `import CertificateSection ...` 줄 아래)에 추가:

```ts
import GapPeriodSection from '@/views/applicant/application/sections/GapPeriodSection.vue'
```

`sectionComponentMap`에서

```ts
  GAP_PERIOD: ApplicationSectionPlaceholder,
```

를 다음으로 교체:

```ts
  GAP_PERIOD: GapPeriodSection,
```

- [ ] **Step 5: 타입체크**

Run: `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 6: (사용자 승인 시) 커밋**

```bash
git add src/types/application/sections/gapPeriod.ts src/api/application/sections/gapPeriodApi.ts src/views/applicant/application/sections/GapPeriodSection.vue src/views/applicant/ApplicationFormView.vue
git commit -m "feat(application): 공백기간(GAP_PERIOD) 폼 섹션 컴포넌트 추가 및 폼 뷰 연동"
```

---

## Task 2: 자기소개(QuestionAnswer) 섹션

**Files:**
- Create: `src/types/application/sections/questionAnswer.ts`
- Create: `src/api/application/sections/questionAnswerApi.ts`
- Create: `src/views/applicant/application/sections/QuestionAnswerSection.vue`
- Modify: `src/views/applicant/ApplicationFormView.vue`

계약: `GET /applications/{id}/questions` → 질문 목록(+기존 답변). `POST /applications/{id}/answers` body `{ answers: [{ questionId, answerText }] }`. 추가/삭제·NA 없음. `answerType` `SHORT_TEXT`→input / `LONG_TEXT`→textarea. 질문별 `required`/`minLength`/`maxLength`. draft는 부분 저장 허용(필수/최소글자수는 최종 제출에서만).

- [ ] **Step 1: 타입 파일 생성**

`src/types/application/sections/questionAnswer.ts`:

```ts
export type QuestionAnswerType = 'SHORT_TEXT' | 'LONG_TEXT'

export type QuestionCategory = 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC'

// 폼에서 다루는 질문 + 가변 답변(answerText)
export interface ApplicationQuestionItem {
  questionId: number
  questionText: string
  helperText?: string
  category: QuestionCategory
  answerType: QuestionAnswerType
  required: boolean
  minLength?: number
  maxLength?: number
  sortOrder: number
  answerId?: number
  answerText: string
}

export interface ApplicationAnswerRequestItem {
  questionId: number
  answerText: string
}

export interface ApplicationAnswerReplaceRequest {
  answers: ApplicationAnswerRequestItem[]
}

// GET 응답 항목(서버 원본; nullable 필드 포함)
export interface ApplicationQuestionResponse {
  questionId: number
  questionText: string
  helperText?: string | null
  category: QuestionCategory
  answerType: QuestionAnswerType
  required: boolean
  minLength?: number | null
  maxLength?: number | null
  sortOrder: number
  answerId?: number | null
  answerText?: string | null
  updatedAt?: string | null
}
```

- [ ] **Step 2: API 모듈 생성**

`src/api/application/sections/questionAnswerApi.ts`:

```ts
import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type {
  ApplicationAnswerReplaceRequest,
  ApplicationQuestionResponse,
} from '@/types/application/sections/questionAnswer'

export const questionAnswerApi = {
  getApplicationsQuestions(applicationId: number) {
    return apiClient.get<ApiResponse<ApplicationQuestionResponse[]>>(`applications/${applicationId}/questions`)
  },

  replaceApplicationsAnswers(applicationId: number, payload: ApplicationAnswerReplaceRequest) {
    return apiClient.post<ApiResponse<ApplicationQuestionResponse[]>>(`applications/${applicationId}/answers`, payload)
  },
}
```

- [ ] **Step 3: 컴포넌트 생성**

`src/views/applicant/application/sections/QuestionAnswerSection.vue`:

```vue
<template>
  <div class="section-body">
    <div v-if="items.length === 0" class="empty-box">
      <p class="empty-title">등록된 질문이 없습니다.</p>
      <p class="empty-desc">이 공고에는 작성할 자기소개 질문이 없습니다.</p>
    </div>

    <div v-else class="card-list">
      <div v-for="item in items" :key="item.questionId" class="item-card">
        <div class="q-head">
          <span class="category-chip">{{ categoryLabel(item.category) }}</span>
          <span v-if="item.required" class="req-badge">필수</span>
        </div>

        <p class="q-text">{{ item.questionText }}<em v-if="item.required"> *</em></p>
        <p v-if="item.helperText" class="q-helper">{{ item.helperText }}</p>

        <a-input
          v-if="item.answerType === 'SHORT_TEXT'"
          v-model:value="item.answerText"
          :maxlength="item.maxLength ?? 5000"
          show-count
          placeholder="답변을 입력하세요."
        />
        <a-textarea
          v-else
          v-model:value="item.answerText"
          :maxlength="item.maxLength ?? 5000"
          :rows="4"
          show-count
          placeholder="답변을 입력하세요."
        />

        <p v-if="item.minLength" class="q-min">최소 {{ item.minLength }}자</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { questionAnswerApi } from '@/api/application/sections/questionAnswerApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  QuestionCategory,
  ApplicationQuestionItem,
  ApplicationQuestionResponse,
  ApplicationAnswerReplaceRequest,
} from '@/types/application/sections/questionAnswer'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const items = reactive<ApplicationQuestionItem[]>([])

const CATEGORY_LABELS: Record<QuestionCategory, string> = {
  SELF_INTRODUCTION: '자기소개',
  GENERAL: '일반',
  JOB_SPECIFIC: '직무',
  ETC: '기타',
}

function categoryLabel(category: QuestionCategory): string {
  return CATEGORY_LABELS[category] ?? category
}

function setItems(list: ApplicationQuestionResponse[]) {
  const mapped = list.map((row) => ({
    questionId: row.questionId,
    questionText: row.questionText,
    helperText: row.helperText ?? undefined,
    category: row.category,
    answerType: row.answerType,
    required: row.required,
    minLength: row.minLength ?? undefined,
    maxLength: row.maxLength ?? undefined,
    sortOrder: row.sortOrder,
    answerId: row.answerId ?? undefined,
    answerText: row.answerText ?? '',
  }))
  mapped.sort((a, b) => a.sortOrder - b.sortOrder)
  items.splice(0, items.length, ...mapped)
}

function buildPayload(): ApplicationAnswerReplaceRequest {
  return {
    answers: items
      .filter((item) => item.answerText.trim().length > 0)
      .map((item) => ({ questionId: item.questionId, answerText: item.answerText })),
  }
}

function validate(): boolean {
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    const text = item.answerText.trim()
    if (item.required && !text) {
      message.warning(`'${item.questionText}'은(는) 필수 답변입니다.`)
      return false
    }
    if (text && item.minLength && text.length < item.minLength) {
      message.warning(`'${item.questionText}'은(는) 최소 ${item.minLength}자 이상 입력하세요.`)
      return false
    }
    if (item.maxLength && item.answerText.length > item.maxLength) {
      message.warning(`'${item.questionText}'은(는) 최대 ${item.maxLength}자까지 입력할 수 있습니다.`)
      return false
    }
  }
  return true
}

async function loadQuestions() {
  loading.value = true
  try {
    const result = await questionAnswerApi.getApplicationsQuestions(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  loading.value = true
  try {
    const result = await questionAnswerApi.replaceApplicationsAnswers(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_QUESTION_ANSWER',
      operation: 'SAVE_DRAFT_QUESTION_ANSWER',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '자기소개 답변 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadQuestions()
})

defineExpose({ saveDraft, validateBeforeSubmit })
</script>

<style scoped>
.section-body {
  margin: 24px;
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.item-card {
  border: 1px solid #eef1ee;
  border-radius: 12px;
  background: #fff;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.category-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 6px;
  background: #f4f8f0;
  color: #536d2f;
  font-size: 12px;
  font-weight: 800;
}
.req-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border-radius: 4px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  font-size: 12px;
  font-weight: 600;
}
.q-text {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.5;
}
.q-helper {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}
.q-min {
  margin: 6px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
.empty-box {
  padding: 34px 20px;
  text-align: center;
  border: 1px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafafa;
}
.empty-title {
  margin: 0 0 4px;
  color: #6b7280;
  font-size: 14px;
  font-weight: 600;
}
.empty-desc {
  margin: 0;
  color: #9ca3af;
  font-size: 13px;
}
em {
  color: #ff4d4f;
  font-style: normal;
}
:deep(.ant-input),
:deep(.ant-input-affix-wrapper),
:deep(.ant-input-textarea) {
  width: 100%;
}
</style>
```

- [ ] **Step 4: ApplicationFormView에 와이어링**

import 블록(`import GapPeriodSection ...` 줄 아래)에 추가:

```ts
import QuestionAnswerSection from '@/views/applicant/application/sections/QuestionAnswerSection.vue'
```

`sectionComponentMap`에서

```ts
  QUESTION_ANSWER: ApplicationSectionPlaceholder,
```

를 다음으로 교체:

```ts
  QUESTION_ANSWER: QuestionAnswerSection,
```

- [ ] **Step 5: 타입체크**

Run: `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 6: (사용자 승인 시) 커밋**

```bash
git add src/types/application/sections/questionAnswer.ts src/api/application/sections/questionAnswerApi.ts src/views/applicant/application/sections/QuestionAnswerSection.vue src/views/applicant/ApplicationFormView.vue
git commit -m "feat(application): 자기소개(QUESTION_ANSWER) 폼 섹션 컴포넌트 추가 및 폼 뷰 연동"
```

---

## Task 3: API 계약 문서 갱신

**Files:**
- Modify: `api-contract.md` (recruit 통합 루트)

- [ ] **Step 1: 공백기간·자기소개 섹션 추가**

`api-contract.md`의 `### 화면: 지원자 자기소개/질문 ...` 또는 적절한 위치(예: 자격증 섹션 다음, 기본정보 섹션 앞)에 아래 두 블록을 추가:

```text
### 화면: 지원자 공백기간 (ApplicationGapPeriod)

- 프론트: `src/api/application/sections/gapPeriodApi.ts`, `src/views/applicant/application/sections/GapPeriodSection.vue` (ApplicationFormView `sectionComponentMap.GAP_PERIOD`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationGapPeriodController`

#### GET·POST `/api/applications/{applicationId}/gap-periods`  🟢

- 요청: `{ gapPeriods: [{ startDate, endDate, gapType, reason, description, sortOrder }] }`
- 응답(200): `ApiResponse<[{ gapPeriodId, startDate, endDate, gapType, reason, description, sortOrder }]>`
- 필수: `startDate`, `endDate`, `gapType`, `reason`. `description`은 선택(≤2000).
- `gapType`은 enum `EDUCATION`/`CAREER`/`OTHER`(프론트 라벨 학업/경력/기타, 하드코딩 — 공통코드 아님).
- 공백기간은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식. 프론트 "해당 사항 없음" 체크박스는 영속화 부재로 주석 처리됨.

### 화면: 지원자 자기소개/질문 (ApplicationQuestionAnswer)

- 프론트: `src/api/application/sections/questionAnswerApi.ts`, `src/views/applicant/application/sections/QuestionAnswerSection.vue` (ApplicationFormView `sectionComponentMap.QUESTION_ANSWER`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAnswerController`

#### GET `/api/applications/{applicationId}/questions`  🟢

- 응답(200): `ApiResponse<[{ questionId, questionText, helperText, category, answerType, required, minLength, maxLength, sortOrder, answerId, answerText, updatedAt }]>`
- 공고가 정의한 질문(JobPostingQuestion) + 지원자 기존 답변 병합. `category` enum SELF_INTRODUCTION/GENERAL/JOB_SPECIFIC/ETC, `answerType` enum SHORT_TEXT/LONG_TEXT.

#### POST `/api/applications/{applicationId}/answers`  🟢

- 요청: `{ answers: [{ questionId, answerText }] }` (answerText ≤5000, 전체 교체)
- 응답(200): `GET /questions`와 동일 형태(질문 + 갱신된 답변).
- draft 부분 저장 허용(answerText NotBlank 아님). 필수/minLength는 프론트 최종 제출 검증에서만 강제.
```

- [ ] **Step 2: (사용자 승인 시) 커밋** (recruit 루트 로컬 git)

```bash
git add api-contract.md
git commit -m "docs(contract): 공백기간·자기소개 계약 추가 (프론트 반영 완료)"
```

---

## Task 4: 최종 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 타입체크**

Run(`recruit_front/`): `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 2: 빌드**

Run(`recruit_front/`): `npm run build`
Expected: 빌드 성공(exit 0).

- [ ] **Step 3: (선택) 시각 확인**

백엔드 기동 + 유효 `applicationId`가 있으면 `npm run dev` 후 `/applicant/:applicationId/form`에서 공백기간/자기소개 단계로 이동해 확인. 자기소개는 공고에 질문(`JobPostingQuestion`)이 시드되어야 카드가 표시됨(없으면 빈 상태 — 정상). 백엔드 미가용 시 type-check/build 통과로 갈음.

---

## Self-Review (계획 작성자 점검 결과)

**1. Spec coverage:** 설계서 §5 파일계획(6 신규/2 수정) → Task 1~3 모두 반영. §3 계약(gap-periods, questions/answers) → 타입/API/검증 반영. §4 결정 A(gapType 학업/경력/기타)→`gapTypeOptions`; B(카테고리 칩)→`CATEGORY_LABELS`/`categoryLabel`; C(컴포넌트명)→`GapPeriodSection`/`QuestionAnswerSection`. §6 동작(공백기간 카드/NA주석/replace, 자기소개 질문렌더/부분저장/required·minLength 검증)→각 컴포넌트. §7 계약문서→Task 3. §9 검증→Task 4. 누락 없음.

**2. Placeholder scan:** "TBD/TODO/적절히" 없음. 모든 코드 단계에 실제 코드 기재. 시각 확인은 환경 의존이라 "선택"으로 명시.

**3. Type consistency:** API 메서드명(`getApplicationsGapPeriods`/`replaceApplicationsGapPeriods`, `getApplicationsQuestions`/`replaceApplicationsAnswers`)·타입명(`GapPeriodItem`/`GapPeriodResponse`/`GapPeriodReplaceRequest`, `ApplicationQuestionItem`/`ApplicationQuestionResponse`/`ApplicationAnswerReplaceRequest`)·`gapTypeOptions`/`categoryLabel`/`setItems`/`buildPayload`/`validate`/`saveDraft`/`validateBeforeSubmit` 일관. `logClientEvent`의 `eventType: 'APPLICATION_DRAFT_SAVE_FAILED'`는 `ClientEventType` 유효 멤버. `noUncheckedIndexedAccess` 가드(`if (!item) continue`) 포함. `gapType: item.gapType as GapType`(validate가 비어있지 않음을 보장한 뒤 buildPayload 호출).
