# 관리자 공통코드 관리 화면 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 `/admin/codes`에서 공통코드를 등록·수정·비활성화하고, 그룹 자체도 `CODE_GROUP` 자기참조 구조로 관리하는 화면을 만든다.

**Architecture:** 백엔드는 Phase 08a에서 이미 완성된 `/api/admin/codes` 3개 엔드포인트를 그대로 쓴다(코드·스키마 변경 없음). 프론트는 화면 진입 시 전체 코드를 1회 조회해 메모리에 두고, 그룹 목록 파생·키워드 검색·비활성 필터를 모두 클라이언트에서 처리한다. 삭제는 `active=false` soft delete뿐이며 비활성화 시 확인 모달로 영향을 경고한다.

**Tech Stack:** Vue 3 (`<script setup lang="ts">`), Vue Router, axios(`apiClient`), ant-design-vue, SCSS scoped.

**설계 문서:** `docs/superpowers/specs/2026-08-28-admin-common-code-manage-design.md`

**검증 정책(프로젝트 규칙 우선):** 이 저장소의 프론트 기본 검증은 `npm run type-check`이며 단위 테스트는 필요 시에만 작성한다(`recruit/CLAUDE.md` §5). 따라서 이 계획은 vitest 기반 TDD 대신 타입 체크 + 수동 시나리오 검증을 사용한다. 커밋은 사용자가 명시 요청할 때만 한다(`recruit/CLAUDE.md` §6).

---

## 파일 구조

| 구분 | 파일 | 책임 |
| --- | --- | --- |
| 신규 | `recruit_front/src/api/adminCommonCodeApi.ts` | 관리자 공통코드 HTTP 호출 3개 |
| 신규 | `recruit_front/src/views/admin/AdminCommonCodeManageView.vue` | 화면 전체(툴바·테이블·모달·확인 흐름) |
| 수정 | `recruit_front/src/types/commonCode.ts` | 생성/수정 요청 타입 추가 |
| 수정 | `recruit_front/src/routes/adminRoutes.ts` | `codes` 라우트 추가 |
| 수정 | `recruit/api-contract.md` | 관리자 CommonCode 화면 섹션 추가 |

---

### Task 1: 타입 + API 모듈

**Files:**
- Modify: `recruit_front/src/types/commonCode.ts`
- Create: `recruit_front/src/api/adminCommonCodeApi.ts`

- [ ] **Step 1: 요청 타입 추가**

`recruit_front/src/types/commonCode.ts` 파일 끝에 아래를 덧붙인다. 기존 `CommonCodeItems`, `CommonCodeResponse`는 그대로 둔다.

```ts

/* 생성 요청. groupCode/code는 생성 시에만 지정할 수 있고 이후 불변이다. */
export interface CommonCodeCreateRequest {
  groupCode: string
  code: string
  displayName: string
  sortOrder: number
  active: boolean
  description: string | null
}

/* 수정 요청. groupCode/code는 불변이라 포함하지 않는다. active=false가 soft delete다. */
export interface CommonCodeUpdateRequest {
  displayName: string
  sortOrder: number
  active: boolean
  description: string | null
}
```

- [ ] **Step 2: API 모듈 생성**

`recruit_front/src/api/adminCommonCodeApi.ts` 를 아래 내용으로 만든다.

```ts
import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type {
  CommonCodeCreateRequest,
  CommonCodeItems,
  CommonCodeUpdateRequest,
} from '@/types/commonCode'

/*
 * 관리자 공통코드 관리 전용(/api/admin/codes/**, ROLE_ADMIN·ROLE_RECRUIT_ADMIN).
 * 백엔드가 GET/POST만 쓰는 관례라 수정도 POST다. 삭제 API는 없고 active=false soft delete만 있다.
 * groupCode를 생략하면 전체를 그룹/정렬 순으로 돌려준다(화면은 이 형태만 쓴다).
 */
export const adminCommonCodeApi = {
  fetchCodes(groupCode?: string) {
    return apiClient.get<ApiResponse<CommonCodeItems[]>>('/admin/codes', {
      params: groupCode ? { groupCode } : undefined,
    })
  },

  createCode(request: CommonCodeCreateRequest) {
    return apiClient.post<ApiResponse<CommonCodeItems>>('/admin/codes', request)
  },

  updateCode(id: number, request: CommonCodeUpdateRequest) {
    return apiClient.post<ApiResponse<CommonCodeItems>>(`/admin/codes/${id}`, request)
  },
}
```

- [ ] **Step 3: 타입 체크**

Run: `cd recruit_front && npm run type-check`
Expected: 통과(에러 0건). 이 시점에는 아직 사용하는 화면이 없으므로 미사용 경고도 발생하지 않는다.

---

### Task 2: 라우트 등록

**Files:**
- Modify: `recruit_front/src/routes/adminRoutes.ts`

- [ ] **Step 1: 라우트 추가**

`faqs` 라우트 블록 바로 아래, `job-postings` 블록 위에 다음을 삽입한다.

```ts
      {
        path: 'codes',
        name: 'AdminCommonCodeManage',
        component: () => import('@/views/admin/AdminCommonCodeManageView.vue'),
      },
```

인증·권한은 부모 `/admin` 라우트의 `meta`(`requiresAuth`, `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN`)를 상속하므로 추가 지정하지 않는다.

- [ ] **Step 2: 확인**

Run: `cd recruit_front && npm run type-check`
Expected: `AdminCommonCodeManageView.vue`가 아직 없으므로 모듈 미해결 에러가 난다. Task 3에서 파일을 만들면 해소된다. (Task 2·3을 연속으로 진행할 것.)

---

### Task 3: 화면 컴포넌트

**Files:**
- Create: `recruit_front/src/views/admin/AdminCommonCodeManageView.vue`

- [ ] **Step 1: 컴포넌트 파일 생성**

`recruit_front/src/views/admin/AdminCommonCodeManageView.vue` 를 아래 내용 그대로 만든다.

```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'

import { adminCommonCodeApi } from '@/api/adminCommonCodeApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { CommonCodeItems } from '@/types/commonCode'

/*
 * 그룹 목록을 담는 테이블이 없어서 그룹 자체를 이 그룹의 코드 행으로 관리한다(self-host).
 * CODE_GROUP 행의 description이 곧 "이 그룹을 어느 화면에서 쓰는지" 메모다.
 */
const GROUP_OF_GROUPS = 'CODE_GROUP'

/* 백엔드 ApplicationBasicInfoService가 active=true를 검증하는 그룹. 비활성화하면 지원자 저장이 실패한다. */
const VALIDATED_GROUPS = ['NATIONALITY', 'DISABILITY_TYPE', 'DISABILITY_GRADE']

interface GroupOption {
  value: string
  label: string
  registered: boolean
  description: string
}

interface CodeForm {
  groupCode: string
  code: string
  displayName: string
  sortOrder: number
  description: string
  active: boolean
}

const codes = ref<CommonCodeItems[]>([])
const loading = ref(false)
const selectedGroup = ref('')
const keyword = ref('')
const includeInactive = ref(true)

const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CodeForm>({
  groupCode: '',
  code: '',
  displayName: '',
  sortOrder: 10,
  description: '',
  active: true,
})

/* ---------- 파생 ---------- */

/* CODE_GROUP에 등록된 행. 그룹 셀렉트의 한글 라벨과 사용 화면 메모의 출처다. */
const groupRows = computed(() =>
  codes.value.filter((item) => item.groupCode === GROUP_OF_GROUPS),
)

/*
 * 그룹 목록 = CODE_GROUP 등록분 + 데이터에만 존재하는 미등록 groupCode.
 * 등록만 되고 아직 코드가 없는 그룹도 보여야 하므로 두 집합을 합친다.
 */
const groupOptions = computed<GroupOption[]>(() => {
  const registered: GroupOption[] = groupRows.value.map((row) => ({
    value: row.code,
    label: `${row.displayName} · ${row.code}`,
    registered: true,
    description: row.description ?? '',
  }))
  const registeredCodes = new Set(registered.map((option) => option.value))
  const unregistered: GroupOption[] = [...new Set(codes.value.map((item) => item.groupCode))]
    .filter((groupCode) => !registeredCodes.has(groupCode))
    .sort()
    .map((groupCode) => ({
      value: groupCode,
      label: `${groupCode} (미등록)`,
      registered: false,
      description: '',
    }))
  return [...registered, ...unregistered]
})

const currentGroup = computed<GroupOption | undefined>(() =>
  groupOptions.value.find((option) => option.value === selectedGroup.value),
)

const isGroupOfGroups = computed(() => selectedGroup.value === GROUP_OF_GROUPS)
const isValidatedGroup = computed(() => VALIDATED_GROUPS.includes(selectedGroup.value))

const rows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return codes.value
    .filter((item) => item.groupCode === selectedGroup.value)
    .filter((item) => includeInactive.value || item.active)
    .filter(
      (item) =>
        !kw ||
        item.code.toLowerCase().includes(kw) ||
        item.displayName.toLowerCase().includes(kw),
    )
})

const columns = computed(() => [
  { title: '순서', key: 'sortOrder', width: 64, align: 'center' as const },
  { title: '코드', key: 'code', width: 210 },
  { title: '표시명', key: 'displayName', width: 150 },
  { title: isGroupOfGroups.value ? '설명 · 사용 화면' : '설명', key: 'description' },
  { title: '상태', key: 'active', width: 78, align: 'center' as const },
  { title: '관리', key: 'actions', width: 150, align: 'center' as const },
])

/* ---------- 조회 ---------- */

const loadCodes = async (): Promise<void> => {
  loading.value = true
  try {
    /* 그룹 목록도 같은 응답에서 파생하므로 groupCode 없이 전체를 한 번에 받는다. */
    const response = await adminCommonCodeApi.fetchCodes()
    codes.value = response.data.data ?? []

    if (!groupOptions.value.some((option) => option.value === selectedGroup.value)) {
      selectedGroup.value = groupOptions.value[0]?.value ?? ''
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '공통코드 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

/* ---------- 폼 ---------- */

const nextSortOrder = (groupCode: string): number => {
  const orders = codes.value
    .filter((item) => item.groupCode === groupCode)
    .map((item) => item.sortOrder)
  return orders.length > 0 ? Math.max(...orders) + 10 : 10
}

const openCreate = (groupCode: string, presetCode = ''): void => {
  editingId.value = null
  form.groupCode = groupCode
  form.code = presetCode
  form.displayName = ''
  form.sortOrder = nextSortOrder(groupCode)
  form.description = ''
  form.active = true
  modalOpen.value = true
}

const openEdit = (record: CommonCodeItems): void => {
  editingId.value = record.id
  form.groupCode = record.groupCode
  form.code = record.code
  form.displayName = record.displayName
  form.sortOrder = record.sortOrder
  form.description = record.description ?? ''
  form.active = record.active
  modalOpen.value = true
}

/* 사용 화면 메모는 CODE_GROUP에 있는 현재 그룹 행을 편집하는 것이다. 행이 없으면 그룹부터 등록한다. */
const openGroupMemo = (): void => {
  const row = groupRows.value.find((item) => item.code === selectedGroup.value)
  if (row) {
    openEdit(row)
    return
  }
  openCreate(GROUP_OF_GROUPS, selectedGroup.value)
}

const save = async (): Promise<void> => {
  const groupCode = form.groupCode.trim()
  const code = form.code.trim()
  const displayName = form.displayName.trim()

  if (!groupCode || !code || !displayName) {
    message.warning('그룹코드, 코드, 표시명은 필수입니다.')
    return
  }

  saving.value = true
  try {
    if (editingId.value === null) {
      await adminCommonCodeApi.createCode({
        groupCode,
        code,
        displayName,
        sortOrder: form.sortOrder,
        active: form.active,
        description: form.description.trim() || null,
      })
      message.success('코드를 등록했습니다.')
    } else {
      await adminCommonCodeApi.updateCode(editingId.value, {
        displayName,
        sortOrder: form.sortOrder,
        active: form.active,
        description: form.description.trim() || null,
      })
      message.success('코드를 수정했습니다.')
    }
    modalOpen.value = false
    /* 그룹 셀렉트 라벨도 같은 응답에서 파생되므로 전체를 다시 읽는다. */
    await loadCodes()
    if (editingId.value === null) {
      selectedGroup.value = groupCode
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

/* ---------- 활성 토글 ---------- */

const applyActive = async (record: CommonCodeItems, active: boolean): Promise<void> => {
  try {
    await adminCommonCodeApi.updateCode(record.id, {
      displayName: record.displayName,
      sortOrder: record.sortOrder,
      active,
      description: record.description ?? null,
    })
    message.success(active ? '코드를 활성화했습니다.' : '코드를 비활성화했습니다.')
    await loadCodes()
  } catch (error) {
    message.error(getApiErrorMessage(error, '상태를 변경하지 못했습니다.'))
  }
}

const toggleActive = (record: CommonCodeItems): void => {
  if (!record.active) {
    void applyActive(record, true)
    return
  }

  const base =
    '지원자 화면 선택지에서 즉시 사라집니다. 이미 이 코드로 저장된 지원서는 값이 남지만 표시명이 비어 보일 수 있습니다. 다시 활성화하면 복구됩니다.'
  const extra = isValidatedGroup.value
    ? ' 이 그룹은 백엔드 검증에 사용되므로, 이 코드를 가진 지원자가 지원서를 다시 저장하면 실패합니다.'
    : isGroupOfGroups.value
      ? ' 그룹 행이라 하위 코드는 계속 동작하며 이 화면의 그룹 목록에서만 가려집니다.'
      : ''

  Modal.confirm({
    title: `"${record.displayName}" 코드를 비활성화할까요?`,
    content: base + extra,
    okText: '비활성화',
    okType: 'danger',
    cancelText: '취소',
    onOk: () => applyActive(record, false),
  })
}

onMounted(loadCodes)
</script>

<template>
  <div>
    <header class="page-header">
      <h1 class="page-title">공통코드 관리</h1>
      <p class="page-desc">
        지원서·관리자 화면의 선택지를 관리합니다. 그룹 자체는 <code>CODE_GROUP</code> 그룹에서 관리하며,
        삭제 대신 비활성화만 제공합니다.
      </p>
    </header>

    <section class="panel">
      <div class="panel-header">
        <span class="toolbar">
          <a-select
            v-model:value="selectedGroup"
            class="group-select"
            show-search
            placeholder="그룹 선택"
            :options="groupOptions"
          />
          <a-input v-model:value="keyword" class="keyword-input" placeholder="코드 · 표시명 검색" allow-clear />
          <a-checkbox v-model:checked="includeInactive">비활성 포함</a-checkbox>
        </span>
        <a-button type="primary" @click="openCreate(selectedGroup)">+ 코드 등록</a-button>
      </div>

      <div class="panel-notice">
        <a-alert
          v-if="currentGroup && !currentGroup.registered"
          type="warning"
          show-icon
          message="CODE_GROUP에 등록되지 않은 그룹입니다. 한글명과 사용 화면 메모가 없습니다."
        >
          <template #action>
            <a-button size="small" @click="openGroupMemo">그룹 등록</a-button>
          </template>
        </a-alert>

        <a-alert
          v-else-if="isGroupOfGroups"
          type="info"
          show-icon
          message="그룹 자체를 코드로 관리합니다. 설명란이 곧 그룹의 사용 화면 메모입니다."
        />

        <a-alert
          v-else-if="isValidatedGroup"
          type="warning"
          show-icon
          message="백엔드 검증 결합 그룹입니다. 코드를 비활성화하면 해당 값을 가진 지원자의 지원서 저장이 실패합니다."
        />

        <p v-if="currentGroup && currentGroup.registered" class="usage-line">
          <a-tag color="blue">사용 화면</a-tag>
          <span v-if="currentGroup.description">{{ currentGroup.description }}</span>
          <span v-else class="usage-empty">미기재 — CODE_GROUP의 이 그룹 행 설명에 적어두세요.</span>
          <a-button size="small" @click="openGroupMemo">메모 편집</a-button>
        </p>
      </div>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'sortOrder'">
            <span class="row-order">{{ record.sortOrder }}</span>
          </template>

          <template v-else-if="column.key === 'code'">
            <code class="row-code">{{ record.code }}</code>
          </template>

          <template v-else-if="column.key === 'displayName'">
            <span :class="{ inactive: !record.active }">{{ record.displayName }}</span>
          </template>

          <template v-else-if="column.key === 'description'">
            <span class="row-description">{{ record.description || '—' }}</span>
          </template>

          <template v-else-if="column.key === 'active'">
            <a-tag :color="record.active ? 'green' : 'default'">
              {{ record.active ? '활성' : '비활성' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'actions'">
            <span class="row-actions">
              <a-button size="small" @click="openEdit(record)">수정</a-button>
              <a-button size="small" :danger="record.active" @click="toggleActive(record)">
                {{ record.active ? '비활성화' : '활성화' }}
              </a-button>
            </span>
          </template>
        </template>
      </a-table>
    </section>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId === null ? '코드 등록' : '코드 수정'"
      :confirm-loading="saving"
      ok-text="저장"
      cancel-text="취소"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item
          label="그룹코드"
          :extra="editingId === null ? '새 그룹이면 직접 입력할 수 있습니다.' : undefined"
          required
        >
          <a-input v-model:value="form.groupCode" :maxlength="100" :disabled="editingId !== null" />
        </a-form-item>

        <a-form-item
          label="코드"
          :extra="editingId === null ? undefined : '그룹코드와 코드는 생성 후 변경할 수 없습니다.'"
          required
        >
          <a-input v-model:value="form.code" :maxlength="100" :disabled="editingId !== null" />
        </a-form-item>

        <a-form-item label="표시명" required>
          <a-input v-model:value="form.displayName" :maxlength="200" />
        </a-form-item>

        <a-form-item label="정렬순서">
          <a-input-number v-model:value="form.sortOrder" :min="0" style="width: 140px" />
        </a-form-item>

        <a-form-item
          :label="form.groupCode === GROUP_OF_GROUPS ? '설명 · 사용 화면' : '설명'"
          :extra="
            form.groupCode === GROUP_OF_GROUPS
              ? '예) 지원서 > 기본정보 국적 셀렉트 · 백엔드 검증 결합'
              : undefined
          "
        >
          <a-textarea v-model:value="form.description" :maxlength="500" :rows="3" />
        </a-form-item>

        <a-form-item>
          <a-switch v-model:checked="form.active" />
          <span class="switch-label">활성</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.panel {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  box-shadow: var(--app-shadow-soft);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--app-border-default);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.group-select {
  width: 280px;
}

.keyword-input {
  width: 200px;
}

.panel-notice {
  padding: 12px 16px 0;
}

.usage-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.usage-empty {
  color: var(--app-text-muted);
}

.row-order {
  color: var(--app-text-muted);
}

.row-code {
  font-family: Consolas, monospace;
  font-size: 12px;
}

.row-description {
  color: var(--app-text-secondary);
}

.row-actions {
  display: inline-flex;
  gap: 6px;
}

.inactive {
  color: var(--app-text-muted);
}

.switch-label {
  margin-left: 8px;
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: 타입 체크**

Run: `cd recruit_front && npm run type-check`
Expected: PASS. Task 2의 모듈 미해결 에러도 함께 사라진다.

- [ ] **Step 3: 빌드 확인**

Run: `cd recruit_front && npm run build`
Expected: 성공. 실패하면 에러 메시지에 표시된 파일만 수정하고 다시 실행한다.

---

### Task 4: 수동 시나리오 검증

**Files:** 없음 (실행 확인만)

- [ ] **Step 1: 개발 서버 기동 후 화면 진입**

`/admin/codes` 로 접근한다. `ROLE_ADMIN` 또는 `ROLE_RECRUIT_ADMIN` 계정이어야 한다.
Expected: 그룹 셀렉트에 기존 데이터의 groupCode가 나타나고(초기에는 모두 `(미등록)`), 테이블에 해당 그룹 코드가 정렬순서대로 보인다.

- [ ] **Step 2: 그룹 등록**

`+ 코드 등록` → 그룹코드 `CODE_GROUP`, 코드 `NATIONALITY`, 표시명 `국적`, 설명 `지원서 > 기본정보 국적 셀렉트 · 백엔드 검증 결합` 으로 저장한다.
Expected: 저장 후 그룹 셀렉트의 해당 항목이 `국적 · NATIONALITY`로 바뀌고, `(미등록)` 표기가 사라진다.

- [ ] **Step 3: 사용 화면 메모 확인**

그룹 셀렉트에서 `국적 · NATIONALITY` 를 선택한다.
Expected: `사용 화면` 태그와 함께 메모가 보이고, `백엔드 검증 결합 그룹` 경고 배너가 뜬다. `메모 편집` 버튼은 `CODE_GROUP`의 해당 행 수정 모달을 연다.

- [ ] **Step 4: 코드 등록과 지원자 화면 반영**

`NATIONALITY` 그룹에서 코드 `KR` / 표시명 `대한민국` 을 등록한 뒤, 지원서 작성 > 기본정보 화면의 국적 셀렉트를 확인한다.
Expected: 등록한 코드가 선택지로 나타난다.

- [ ] **Step 5: 비활성화와 복구**

`KR` 행의 `비활성화` 를 누른다.
Expected: 확인 모달에 공통 경고 + 백엔드 검증 결합 경고가 함께 나온다. 확인 후 행은 `비활성` 태그가 되고, 지원자 화면 셀렉트에서 사라진다. `활성화` 를 누르면 확인 없이 즉시 복구된다.

- [ ] **Step 6: 수정 제약 확인**

아무 코드의 `수정` 을 연다.
Expected: 그룹코드·코드 입력이 disabled이고 "그룹코드와 코드는 생성 후 변경할 수 없습니다." 안내가 보인다.

- [ ] **Step 7: 중복 등록 오류 확인**

이미 있는 그룹코드+코드 조합으로 다시 등록을 시도한다.
Expected: 백엔드 메시지("이미 존재하는 코드입니다. groupCode=..., code=...")가 그대로 토스트로 표시된다.

---

### Task 5: API 계약 문서 갱신

**Files:**
- Modify: `recruit/api-contract.md`

- [ ] **Step 1: 화면 섹션 추가**

`api-contract.md` 맨 끝(FAQ 섹션 뒤)에 아래를 추가한다.

```markdown

### 화면: 관리자 공통코드 관리 (AdminCommonCodeManageView)  🟢 확정(2026-08-28)

- 백엔드는 Phase 08a 기구현분을 그대로 사용하며 이번 화면 작업으로 변경되지 않았다
- 그룹 목록을 담는 테이블이 없어 그룹 자체를 `groupCode=CODE_GROUP` 의 코드 행으로 관리한다(self-host). 이 행의 `description`이 그룹의 사용 화면 메모다
- 삭제 API는 없다. 삭제는 `active=false` soft delete로만 처리한다
- `groupCode`/`code`는 생성 후 불변이라 수정 요청에 포함하지 않는다

#### GET `/api/admin/codes`  🟢 확정(2026-08-28)

- 설명: 비활성 포함 코드 조회. `groupCode` 생략 시 전체를 `groupCode` → `sortOrder` → `id` 순으로 반환한다. 화면은 그룹 목록도 이 응답에서 파생하므로 항상 생략 형태로 호출한다
- 요청: `?groupCode=NATIONALITY` (선택)
- 응답(200): `ApiResponse<[{ id, groupCode, code, displayName, sortOrder, active, description }]>`

#### POST `/api/admin/codes`  🟢 확정(2026-08-28)

- 설명: 코드 생성. `sortOrder` 미지정 시 0, `active` 미지정 시 true. 화면은 그룹 내 `최대값 + 10`을 기본값으로 채워 보낸다
- 요청: `{ groupCode, code, displayName, sortOrder, active, description }`
- 응답(200): `ApiResponse<{ id, groupCode, code, displayName, sortOrder, active, description }>`
- 오류: 400(필수값 공백, 길이 초과, `groupCode`+`code` 중복)

#### POST `/api/admin/codes/{id}`  🟢 확정(2026-08-28)

- 설명: 코드 수정. `active=false`가 soft delete다. `groupCode`/`code`는 불변이라 요청에 없다
- 요청: `{ displayName, sortOrder, active, description }`
- 응답(200): `ApiResponse<{ id, groupCode, code, displayName, sortOrder, active, description }>`
- 오류: 400(필수값 공백, 길이 초과), 404(미존재)
```

- [ ] **Step 2: 어학 섹션 주석 정합 확인**

`api-contract.md` 121~122행 부근의 "코드 시드 안 함 — 관리자 CommonCode API로 등록한다" 문구가 이번 화면과 모순되지 않는지 확인한다.
Expected: 모순 없음(이제 등록 경로가 이 화면임을 위 섹션이 설명한다). 수정 불필요.

---

### Task 6: 마무리

- [ ] **Step 1: 사이드바 메뉴 등록 안내**

관리자 사이드바는 DB 메뉴 트리(`menuStore.fetchMenuTree('ADMIN')`)에서 렌더된다. 코드 배포만으로는 메뉴에 노출되지 않는다.
사용자에게 "관리자 > 메뉴 관리 화면에서 경로 `/admin/codes` 로 ADMIN 메뉴를 추가해야 사이드바에 나타난다"고 보고한다.

- [ ] **Step 2: 변경 요약 보고**

변경 파일(신규 2 / 수정 3), `npm run type-check` 결과, 수동 시나리오 결과, 계약 문서 갱신분을 보고한다.

- [ ] **Step 3: 커밋 (사용자가 요청한 경우에만)**

`recruit/CLAUDE.md` §6에 따라 명시 요청 없이는 커밋하지 않는다. 요청이 있으면:

```bash
git add recruit_front/src/api/adminCommonCodeApi.ts recruit_front/src/views/admin/AdminCommonCodeManageView.vue recruit_front/src/types/commonCode.ts recruit_front/src/routes/adminRoutes.ts api-contract.md docs
git commit -m "feat(common-code): 관리자 공통코드 관리 화면 추가"
```
