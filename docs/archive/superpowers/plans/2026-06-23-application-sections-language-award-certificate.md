# 지원서 폼 섹션 3종 (어학·수상·자격증) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ApplicationFormView` 마법사에서 placeholder로 렌더되는 `LANGUAGE / AWARD / CERTIFICATE` 섹션을, 목업(`dist/_extracted/*.rawhtml`)을 재현한 실제 카드형 입력 컴포넌트로 교체한다.

**Architecture:** 각 섹션은 `BasicInfoSection.vue`와 동일한 *섹션 컴포넌트 패턴*(view + api + types 수직 슬라이스)으로 만든다. 컴포넌트는 props(`SectionComponentProps`)로 `applicationId`/`section`을 받고, `defineExpose({ saveDraft, validateBeforeSubmit })`로 부모의 임시저장/최종제출 버튼과 연동한다. 백엔드는 이미 구현되어 있고(전체 교체 replace-list 방식) **변경하지 않는다**. 새 라우트도 추가하지 않는다.

**Tech Stack:** Vue 3.5 (`<script setup lang="ts">`), TypeScript, ant-design-vue 4.2.6, `@ant-design/icons-vue`, axios(`apiClient`), Pinia, Vite. 설계서: `docs/superpowers/specs/2026-06-23-application-sections-language-award-certificate-design.md`.

**검증 방식(중요):** 이 프로젝트는 프론트 TDD를 강제하지 않는다(AGENTS.md: 단위 테스트는 "필요 시에만"). 사용자 지시(AGENTS.md/CLAUDE.md)가 스킬의 TDD 규칙보다 우선하므로, 각 태스크의 검증 게이트는 `npm run type-check`(필요 시 `npm run build`)이다. 컴포넌트의 순수 로직 단위 테스트는 선택 사항이며 본 계획의 필수 단계가 아니다.

**Git 규칙(중요):** CLAUDE.md §6 — "명확한 요청 없이 commit/push 금지". 각 태스크 끝의 commit 단계는 **사용자가 커밋을 승인/요청한 경우에만** 실행한다. 커밋은 `recruit_front/`(자체 git)에서 수행한다. 미승인 시 파일 작성/수정까지만 하고 commit 단계는 건너뛴다.

**작업 디렉토리:** 모든 경로는 `recruit_front/` 기준이며, `npm` 명령은 `recruit_front/`에서 실행한다.

---

## File Structure

신규(9):

- `src/types/application/sections/language.ts` — 어학 폼/요청/응답 타입
- `src/types/application/sections/award.ts` — 수상 폼/요청/응답 타입
- `src/types/application/sections/certificate.ts` — 자격증 폼/요청/응답 타입
- `src/api/application/sections/languageApi.ts` — 어학 GET/POST
- `src/api/application/sections/awardApi.ts` — 수상 GET/POST
- `src/api/application/sections/certificateApi.ts` — 자격증 GET/POST
- `src/views/applicant/application/sections/LanguageSection.vue` — 어학 섹션 화면
- `src/views/applicant/application/sections/AwardSection.vue` — 수상 섹션 화면
- `src/views/applicant/application/sections/CertificateSection.vue` — 자격증 섹션 화면

수정(2):

- `src/views/applicant/ApplicationFormView.vue` — 3개 컴포넌트 import + `sectionComponentMap` 3줄 교체
- `api-contract.md`(recruit 루트, `recruit_front`의 상위) — LANGUAGE 🟢 갱신, AWARD·CERTIFICATE 신규 🟢

각 컴포넌트는 **자체 완결**(상태·로직·scoped CSS 포함, 공통 컴포저블 미추출 — 설계 결정 C). 세 컴포넌트의 구조는 유사하나 필드/라벨/타입이 달라 각각 전체 코드를 기재한다.

---

## Task 1: 어학(Language) 섹션

**Files:**
- Create: `src/types/application/sections/language.ts`
- Create: `src/api/application/sections/languageApi.ts`
- Create: `src/views/applicant/application/sections/LanguageSection.vue`
- Modify: `src/views/applicant/ApplicationFormView.vue`

계약: `GET/POST /applications/{id}/languages`, body `{ languages: [...] }`. 항목 필수: `languageName`, `testName`, `examDate`. `conversationalAbility`는 공통코드 `LANGUAGE_CONVERSATION`(권장 시드 `HIGH/MEDIUM/LOW`).

- [ ] **Step 1: 타입 파일 생성**

`src/types/application/sections/language.ts`:

```ts
// 폼 입력용 항목(서버 응답의 languageId를 보존)
export interface LanguageItem {
  languageId?: number
  languageName: string
  testName: string
  scoreOrGrade?: string
  conversationalAbility?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
}

// POST 요청 항목(sortOrder 포함)
export interface LanguageRequestItem {
  languageName: string
  testName: string
  scoreOrGrade?: string
  conversationalAbility?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
  sortOrder: number
}

export interface LanguageReplaceRequest {
  languages: LanguageRequestItem[]
}

export interface LanguageResponse {
  languageId: number
  languageName: string
  testName: string
  scoreOrGrade?: string
  conversationalAbility?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
  sortOrder: number
}
```

- [ ] **Step 2: API 모듈 생성**

`src/api/application/sections/languageApi.ts` (경로 패턴은 `basicInfoApi.ts`/`educationApi.ts`와 동일하게 `../../client`):

```ts
import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { LanguageReplaceRequest, LanguageResponse } from '@/types/application/sections/language'

export const languageApi = {
  getApplicationsLanguages(applicationId: number) {
    return apiClient.get<ApiResponse<LanguageResponse[]>>(`applications/${applicationId}/languages`)
  },

  replaceApplicationsLanguages(applicationId: number, payload: LanguageReplaceRequest) {
    return apiClient.post<ApiResponse<LanguageResponse[]>>(`applications/${applicationId}/languages`, payload)
  },
}
```

- [ ] **Step 3: 컴포넌트 생성**

`src/views/applicant/application/sections/LanguageSection.vue`:

```vue
<template>
  <div class="section-body">
    <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      어학 성적 없음 (해당 사항 없음)
    </a-checkbox>

    <div v-if="notApplicable" class="na-box">어학 성적 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">어학 {{ index + 1 }}</span>
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
                <th>언어<em> *</em></th>
                <td><a-input v-model:value="item.languageName" placeholder="예) 영어" /></td>
                <th>시험명<em> *</em></th>
                <td><a-input v-model:value="item.testName" placeholder="예) TOEIC" /></td>
              </tr>
              <tr>
                <th>점수/등급</th>
                <td><a-input v-model:value="item.scoreOrGrade" placeholder="예) 950점 / 1급 / Level 7" /></td>
                <th>회화능력</th>
                <td>
                  <a-select
                    v-model:value="item.conversationalAbility"
                    :options="conversationOptions"
                    placeholder="선택"
                    allow-clear
                  />
                </td>
              </tr>
              <tr>
                <th>응시일자<em> *</em></th>
                <td><a-date-picker v-model:value="item.examDate" value-format="YYYY-MM-DD" /></td>
                <th>유효기간</th>
                <td><a-date-picker v-model:value="item.expiredDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>주관기관</th>
                <td colspan="3">
                  <a-input v-model:value="item.issuingOrganization" placeholder="예) ETS / 한국산업인력공단" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 어학 성적이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 어학 성적을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 어학 성적 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { languageApi } from '@/api/application/sections/languageApi'
import { commonCodeApi } from '@/api/commonApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type { LanguageItem, LanguageResponse, LanguageReplaceRequest } from '@/types/application/sections/language'
import type { CommonCodeItems } from '@/types/commonCode'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<LanguageItem[]>([])

const conversationList = ref<CommonCodeItems[]>([])
const conversationOptions = computed(() =>
  conversationList.value.map((code) => ({ value: code.code, label: code.displayName })),
)

function createEmptyItem(): LanguageItem {
  return {
    languageName: '',
    testName: '',
    scoreOrGrade: '',
    conversationalAbility: undefined,
    examDate: '',
    expiredDate: '',
    issuingOrganization: '',
  }
}

function setItems(list: LanguageResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      languageId: row.languageId,
      languageName: row.languageName,
      testName: row.testName,
      scoreOrGrade: row.scoreOrGrade ?? '',
      conversationalAbility: row.conversationalAbility ?? undefined,
      examDate: row.examDate ?? '',
      expiredDate: row.expiredDate ?? '',
      issuingOrganization: row.issuingOrganization ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): LanguageReplaceRequest {
  if (notApplicable.value) return { languages: [] }
  return {
    languages: items.map((item, index) => ({
      languageName: item.languageName,
      testName: item.testName,
      scoreOrGrade: item.scoreOrGrade || undefined,
      conversationalAbility: item.conversationalAbility || undefined,
      examDate: item.examDate,
      expiredDate: item.expiredDate || undefined,
      issuingOrganization: item.issuingOrganization || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("어학 성적을 추가하거나 '어학 성적 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.languageName || !item.testName || !item.examDate) {
      message.warning(`어학 ${i + 1}: 언어, 시험명, 응시일자는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadLanguages() {
  loading.value = true
  try {
    const result = await languageApi.getApplicationsLanguages(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function loadConversationCodes() {
  const result = await commonCodeApi.getCommonCodes('LANGUAGE_CONVERSATION')
  conversationList.value = result.data.data ?? []
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await languageApi.replaceApplicationsLanguages(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_LANGUAGE',
      operation: 'SAVE_DRAFT_LANGUAGE',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '어학 정보 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadLanguages()
  loadConversationCodes()
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

`src/views/applicant/ApplicationFormView.vue`의 import 블록(현재 `import BasicInfoSection ...` 줄 아래)에 추가:

```ts
import LanguageSection from '@/views/applicant/application/sections/LanguageSection.vue'
```

같은 파일 `sectionComponentMap`에서 아래 줄을

```ts
  LANGUAGE: ApplicationSectionPlaceholder,
```

다음으로 교체:

```ts
  LANGUAGE: LanguageSection,
```

- [ ] **Step 5: 타입체크 실행 (검증 게이트)**

Run: `npm run type-check`
Expected: 통과(에러 0). 신규 import/타입 정합, 컴포넌트 매핑 검증.

- [ ] **Step 6: (사용자 승인 시) 커밋**

```bash
git add src/types/application/sections/language.ts src/api/application/sections/languageApi.ts src/views/applicant/application/sections/LanguageSection.vue src/views/applicant/ApplicationFormView.vue
git commit -m "feat(application): 어학(LANGUAGE) 폼 섹션 컴포넌트 추가 및 폼 뷰 연동"
```

---

## Task 2: 수상(Award) 섹션

**Files:**
- Create: `src/types/application/sections/award.ts`
- Create: `src/api/application/sections/awardApi.ts`
- Create: `src/views/applicant/application/sections/AwardSection.vue`
- Modify: `src/views/applicant/ApplicationFormView.vue`

계약: `GET/POST /applications/{id}/awards`, body `{ awards: [...] }`. 항목 필수: `awardName`, `awardingOrganization`, `awardDate`. `description`은 선택, 최대 2000자(카운터).

- [ ] **Step 1: 타입 파일 생성**

`src/types/application/sections/award.ts`:

```ts
export interface AwardItem {
  awardId?: number
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
}

export interface AwardRequestItem {
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
  sortOrder: number
}

export interface AwardReplaceRequest {
  awards: AwardRequestItem[]
}

export interface AwardResponse {
  awardId: number
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
  sortOrder: number
}
```

- [ ] **Step 2: API 모듈 생성**

`src/api/application/sections/awardApi.ts`:

```ts
import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { AwardReplaceRequest, AwardResponse } from '@/types/application/sections/award'

export const awardApi = {
  getApplicationsAwards(applicationId: number) {
    return apiClient.get<ApiResponse<AwardResponse[]>>(`applications/${applicationId}/awards`)
  },

  replaceApplicationsAwards(applicationId: number, payload: AwardReplaceRequest) {
    return apiClient.post<ApiResponse<AwardResponse[]>>(`applications/${applicationId}/awards`, payload)
  },
}
```

- [ ] **Step 3: 컴포넌트 생성**

`src/views/applicant/application/sections/AwardSection.vue`:

```vue
<template>
  <div class="section-body">
    <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      수상 이력 없음 (해당 사항 없음)
    </a-checkbox>

    <div v-if="notApplicable" class="na-box">수상 이력 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">수상 {{ index + 1 }}</span>
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
                <th>수상명<em> *</em></th>
                <td><a-input v-model:value="item.awardName" placeholder="예) 전국 대학생 알고리즘 경진대회 대상" /></td>
                <th>수상일자<em> *</em></th>
                <td><a-date-picker v-model:value="item.awardDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>수여기관<em> *</em></th>
                <td colspan="3"><a-input v-model:value="item.awardingOrganization" placeholder="예) 한국정보과학회" /></td>
              </tr>
              <tr>
                <th class="th-top">수상내용</th>
                <td colspan="3">
                  <a-textarea
                    v-model:value="item.description"
                    :maxlength="2000"
                    :rows="2"
                    show-count
                    placeholder="수상 배경, 주제, 본인의 역할 등을 간단히 작성하세요. (선택)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 수상 이력이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 수상 이력을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 수상 이력 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { awardApi } from '@/api/application/sections/awardApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type { AwardItem, AwardResponse, AwardReplaceRequest } from '@/types/application/sections/award'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<AwardItem[]>([])

function createEmptyItem(): AwardItem {
  return {
    awardName: '',
    awardingOrganization: '',
    awardDate: '',
    description: '',
  }
}

function setItems(list: AwardResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      awardId: row.awardId,
      awardName: row.awardName,
      awardingOrganization: row.awardingOrganization,
      awardDate: row.awardDate ?? '',
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

function buildPayload(): AwardReplaceRequest {
  if (notApplicable.value) return { awards: [] }
  return {
    awards: items.map((item, index) => ({
      awardName: item.awardName,
      awardingOrganization: item.awardingOrganization,
      awardDate: item.awardDate,
      description: item.description || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("수상 이력을 추가하거나 '수상 이력 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.awardName || !item.awardingOrganization || !item.awardDate) {
      message.warning(`수상 ${i + 1}: 수상명, 수여기관, 수상일자는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadAwards() {
  loading.value = true
  try {
    const result = await awardApi.getApplicationsAwards(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await awardApi.replaceApplicationsAwards(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_AWARD',
      operation: 'SAVE_DRAFT_AWARD',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '수상 정보 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadAwards()
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
:deep(.ant-picker) {
  width: 100%;
}
</style>
```

- [ ] **Step 4: ApplicationFormView에 와이어링**

import 블록에 추가:

```ts
import AwardSection from '@/views/applicant/application/sections/AwardSection.vue'
```

`sectionComponentMap`에서

```ts
  AWARD: ApplicationSectionPlaceholder,
```

를 다음으로 교체:

```ts
  AWARD: AwardSection,
```

- [ ] **Step 5: 타입체크 실행**

Run: `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 6: (사용자 승인 시) 커밋**

```bash
git add src/types/application/sections/award.ts src/api/application/sections/awardApi.ts src/views/applicant/application/sections/AwardSection.vue src/views/applicant/ApplicationFormView.vue
git commit -m "feat(application): 수상(AWARD) 폼 섹션 컴포넌트 추가 및 폼 뷰 연동"
```

---

## Task 3: 자격증(Certificate) 섹션

**Files:**
- Create: `src/types/application/sections/certificate.ts`
- Create: `src/api/application/sections/certificateApi.ts`
- Create: `src/views/applicant/application/sections/CertificateSection.vue`
- Modify: `src/views/applicant/ApplicationFormView.vue`

계약: `GET/POST /applications/{id}/certificates`, body `{ certificates: [...] }`. 항목 필수: `certificateName`, `issuingOrganization`, `acquiredDate`. 선택: `certificateNumber`, `expiredDate`, `scoreOrGrade`.

- [ ] **Step 1: 타입 파일 생성**

`src/types/application/sections/certificate.ts`:

```ts
export interface CertificateItem {
  certificateId?: number
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
}

export interface CertificateRequestItem {
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
  sortOrder: number
}

export interface CertificateReplaceRequest {
  certificates: CertificateRequestItem[]
}

export interface CertificateResponse {
  certificateId: number
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
  sortOrder: number
}
```

- [ ] **Step 2: API 모듈 생성**

`src/api/application/sections/certificateApi.ts`:

```ts
import { apiClient } from '../../client'
import type { ApiResponse } from '@/types/api'
import type { CertificateReplaceRequest, CertificateResponse } from '@/types/application/sections/certificate'

export const certificateApi = {
  getApplicationsCertificates(applicationId: number) {
    return apiClient.get<ApiResponse<CertificateResponse[]>>(`applications/${applicationId}/certificates`)
  },

  replaceApplicationsCertificates(applicationId: number, payload: CertificateReplaceRequest) {
    return apiClient.post<ApiResponse<CertificateResponse[]>>(`applications/${applicationId}/certificates`, payload)
  },
}
```

- [ ] **Step 3: 컴포넌트 생성**

`src/views/applicant/application/sections/CertificateSection.vue`:

```vue
<template>
  <div class="section-body">
    <a-checkbox v-model:checked="notApplicable" class="na-checkbox">
      보유 자격증 없음 (해당 사항 없음)
    </a-checkbox>

    <div v-if="notApplicable" class="na-box">보유 자격증 없음으로 표시되었습니다.</div>

    <template v-else>
      <div class="card-list">
        <div v-for="(item, index) in items" :key="index" class="item-card">
          <div class="item-card-head">
            <span class="num-pill">자격증 {{ index + 1 }}</span>
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
                <th>자격증명<em> *</em></th>
                <td><a-input v-model:value="item.certificateName" placeholder="예) 정보처리기사" /></td>
                <th>발급기관<em> *</em></th>
                <td><a-input v-model:value="item.issuingOrganization" placeholder="예) 한국산업인력공단" /></td>
              </tr>
              <tr>
                <th>취득일자<em> *</em></th>
                <td><a-date-picker v-model:value="item.acquiredDate" value-format="YYYY-MM-DD" /></td>
                <th>유효기간</th>
                <td><a-date-picker v-model:value="item.expiredDate" value-format="YYYY-MM-DD" /></td>
              </tr>
              <tr>
                <th>자격증번호</th>
                <td><a-input v-model:value="item.certificateNumber" placeholder="예) 20-12-345678" /></td>
                <th>점수/등급</th>
                <td><a-input v-model:value="item.scoreOrGrade" placeholder="예) 1급 / 850점" /></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="items.length === 0" class="empty-box">
        <p class="empty-title">등록된 자격증이 없습니다.</p>
        <p class="empty-desc">아래 버튼으로 자격증을 추가하세요.</p>
      </div>

      <button type="button" class="add-btn" @click="addItem">
        <PlusOutlined /> 자격증 추가
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { certificateApi } from '@/api/application/sections/certificateApi'
import { getApiErrorMessage } from '@/api/apiError'
import { logClientEvent } from '@/common/clientEventLogger'
import type { SectionComponentProps } from '@/types/application'
import type {
  CertificateItem,
  CertificateResponse,
  CertificateReplaceRequest,
} from '@/types/application/sections/certificate'

const props = defineProps<SectionComponentProps>()

const loading = ref(false)
const notApplicable = ref(false)
const items = reactive<CertificateItem[]>([])

function createEmptyItem(): CertificateItem {
  return {
    certificateName: '',
    issuingOrganization: '',
    acquiredDate: '',
    certificateNumber: '',
    expiredDate: '',
    scoreOrGrade: '',
  }
}

function setItems(list: CertificateResponse[]) {
  items.splice(
    0,
    items.length,
    ...list.map((row) => ({
      certificateId: row.certificateId,
      certificateName: row.certificateName,
      issuingOrganization: row.issuingOrganization,
      acquiredDate: row.acquiredDate ?? '',
      certificateNumber: row.certificateNumber ?? '',
      expiredDate: row.expiredDate ?? '',
      scoreOrGrade: row.scoreOrGrade ?? '',
    })),
  )
}

function addItem() {
  items.push(createEmptyItem())
}

function removeItem(index: number) {
  items.splice(index, 1)
}

function buildPayload(): CertificateReplaceRequest {
  if (notApplicable.value) return { certificates: [] }
  return {
    certificates: items.map((item, index) => ({
      certificateName: item.certificateName,
      issuingOrganization: item.issuingOrganization,
      acquiredDate: item.acquiredDate,
      certificateNumber: item.certificateNumber || undefined,
      expiredDate: item.expiredDate || undefined,
      scoreOrGrade: item.scoreOrGrade || undefined,
      sortOrder: index,
    })),
  }
}

function validate(): boolean {
  if (notApplicable.value) return true
  if (items.length === 0) {
    if (props.section.required) {
      message.warning("자격증을 추가하거나 '보유 자격증 없음'을 선택하세요.")
      return false
    }
    return true
  }
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (!item) continue
    if (!item.certificateName || !item.issuingOrganization || !item.acquiredDate) {
      message.warning(`자격증 ${i + 1}: 자격증명, 발급기관, 취득일자는 필수입니다.`)
      return false
    }
  }
  return true
}

async function loadCertificates() {
  loading.value = true
  try {
    const result = await certificateApi.getApplicationsCertificates(props.applicationId)
    setItems(result.data.data ?? [])
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!validate()) throw new Error('입력값을 확인해주세요.')
  loading.value = true
  try {
    const result = await certificateApi.replaceApplicationsCertificates(props.applicationId, buildPayload())
    setItems(result.data.data ?? [])
    return result.data.data
  } catch (error) {
    logClientEvent({
      eventType: 'APPLICATION_DRAFT_SAVE_FAILED',
      severity: 'INFO',
      pageCode: 'APPLICATION_FORM_CERTIFICATE',
      operation: 'SAVE_DRAFT_CERTIFICATE',
      applicationId: props.applicationId,
      message: 'APPLICATION_DRAFT_SAVE_FAILED',
    })
    throw new Error(getApiErrorMessage(error, '자격증 정보 저장에 실패했습니다.'))
  } finally {
    loading.value = false
  }
}

function validateBeforeSubmit(): boolean {
  return validate()
}

onMounted(() => {
  loadCertificates()
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
:deep(.ant-picker) {
  width: 100%;
}
</style>
```

- [ ] **Step 4: ApplicationFormView에 와이어링**

import 블록에 추가:

```ts
import CertificateSection from '@/views/applicant/application/sections/CertificateSection.vue'
```

`sectionComponentMap`에서

```ts
  CERTIFICATE: ApplicationSectionPlaceholder,
```

를 다음으로 교체:

```ts
  CERTIFICATE: CertificateSection,
```

- [ ] **Step 5: 타입체크 실행**

Run: `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 6: (사용자 승인 시) 커밋**

```bash
git add src/types/application/sections/certificate.ts src/api/application/sections/certificateApi.ts src/views/applicant/application/sections/CertificateSection.vue src/views/applicant/ApplicationFormView.vue
git commit -m "feat(application): 자격증(CERTIFICATE) 폼 섹션 컴포넌트 추가 및 폼 뷰 연동"
```

---

## Task 4: API 계약 문서 갱신

**Files:**
- Modify: `api-contract.md` (recruit 통합 루트 — `recruit_front`의 상위 폴더)

- [ ] **Step 1: 어학 섹션 상태 🟢로 갱신**

`api-contract.md`의 다음 줄

```text
#### GET·POST `/api/applications/{applicationId}/languages`  🔴 백엔드 구현됨 / 프론트 미반영
```

을 다음으로 교체:

```text
#### GET·POST `/api/applications/{applicationId}/languages`  🟢

- 프론트: `src/api/application/sections/languageApi.ts`, `src/views/applicant/application/sections/LanguageSection.vue` (ApplicationFormView `sectionComponentMap.LANGUAGE`)
```

(기존 변경 이력/필드 요약 줄들은 그대로 둔다.)

- [ ] **Step 2: 수상·자격증 섹션 신규 추가**

`api-contract.md`의 `### 화면: 지원자 어학 (ApplicationLanguage)` 블록 끝(다음 `###` 시작 전)에 아래 두 섹션을 추가:

```text
### 화면: 지원자 수상 (ApplicationAward)

- 프론트: `src/api/application/sections/awardApi.ts`, `src/views/applicant/application/sections/AwardSection.vue` (ApplicationFormView `sectionComponentMap.AWARD`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationAwardController`

#### GET·POST `/api/applications/{applicationId}/awards`  🟢

- 요청: `{ awards: [{ awardName, awardingOrganization, awardDate, description, sortOrder }] }`
- 응답(200): `ApiResponse<[{ awardId, awardName, awardingOrganization, awardDate, description, sortOrder }]>`
- 필수: `awardName`, `awardingOrganization`, `awardDate`. `description`은 선택, 최대 2000자.
- 수상은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식.

### 화면: 지원자 자격증 (ApplicationCertificate)

- 프론트: `src/api/application/sections/certificateApi.ts`, `src/views/applicant/application/sections/CertificateSection.vue` (ApplicationFormView `sectionComponentMap.CERTIFICATE`)
- 백엔드: `com.shinyoung.recruit.controller.ApplicationCertificateController`

#### GET·POST `/api/applications/{applicationId}/certificates`  🟢

- 요청: `{ certificates: [{ certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }] }`
- 응답(200): `ApiResponse<[{ certificateId, certificateName, issuingOrganization, acquiredDate, certificateNumber, expiredDate, scoreOrGrade, sortOrder }]>`
- 필수: `certificateName`, `issuingOrganization`, `acquiredDate`. 나머지 선택.
- 자격증은 선택(0개 허용 = 빈 배열). 전체 교체(replace) 방식.
```

- [ ] **Step 3: (사용자 승인 시) 커밋**

`api-contract.md`는 recruit 통합 루트(로컬 전용 git). 커밋은 그 저장소에서 수행.

```bash
git add api-contract.md
git commit -m "docs(contract): 어학 🟢 갱신, 수상·자격증 계약 추가 (프론트 반영 완료)"
```

---

## Task 5: 최종 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 타입체크**

Run(`recruit_front/`): `npm run type-check`
Expected: 통과(에러 0).

- [ ] **Step 2: 빌드**

Run(`recruit_front/`): `npm run build`
Expected: 빌드 성공(`run-p type-check build-only` 통과). 산출물 `dist/` 생성.

- [ ] **Step 3: (선택) 시각 확인**

백엔드 기동 + 유효한 `applicationId`가 있으면 `npm run dev` 후 `/applicant/:applicationId/form`에서 어학/수상/자격증 단계로 이동해 카드 추가/삭제·해당없음 토글·임시저장을 확인한다. 백엔드 미가용 시 이 단계는 생략하고 type-check/build 통과로 갈음한다(본 슬라이스 범위는 프론트 구현/빌드까지).

> 회화능력 드롭다운은 공통코드 `LANGUAGE_CONVERSATION`(권장 코드 `HIGH/MEDIUM/LOW`)이 시드되어야 옵션이 표시된다. 미시드 시 빈 드롭다운이며 이는 정상(설계 §8 한계).

---

## Self-Review (계획 작성자 점검 결과)

**1. Spec coverage:** 설계서 §5 파일계획(9 신규/2 수정) → Task 1~4에서 모두 생성/수정. §3 계약 3종 → 각 Task의 타입/API/검증에 반영. §4 결정 A(공통코드)→Task1 Step3 `loadConversationCodes`/`conversationOptions`; B(카드 재현)→각 컴포넌트 template/style; C(자체 완결)→컴포넌트별 독립 코드. §6 동작(load/add/remove/NA/saveDraft/validateBeforeSubmit/sortOrder)→각 컴포넌트 스크립트. §7 계약문서→Task 4. §9 검증→Task 5. 누락 없음.

**2. Placeholder scan:** "TBD/TODO/적절히 처리" 류 없음. 모든 코드 단계에 실제 코드 기재. 시각 확인(Task5 Step3)은 환경 의존이라 "선택"으로 명시(누락이 아니라 범위 한계).

**3. Type consistency:** API 메서드명(`getApplicationsLanguages`/`replaceApplicationsLanguages` 등)·타입명(`LanguageItem`/`LanguageResponse`/`LanguageReplaceRequest` 등)·`setItems`/`buildPayload`/`validate`/`saveDraft`/`validateBeforeSubmit`가 각 컴포넌트 내부에서 일관. `logClientEvent`의 `eventType: 'APPLICATION_DRAFT_SAVE_FAILED'`는 `ClientEventType` 유효 멤버. `commonCodeApi.getCommonCodes` 반환 → `result.data.data: CommonCodeItems[]`, `code`/`displayName` 사용 일치. `SectionComponentProps`(`applicationId`,`section.required`) 사용 일치.
