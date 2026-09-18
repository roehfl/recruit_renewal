# 지원현황 엑셀 컬럼 선택 S2 (프론트) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원현황 조회의 "엑셀 다운로드" 버튼이 컬럼 선택 모달을 열고, 체크한 항목만 엑셀로 받게 한다. 화면 결함 2건(철회 라벨, 졸업년월 컬럼)을 함께 고친다.

**Architecture:** 모달(`ApplicationExcelColumnModal.vue`)은 카탈로그 API 응답으로 체크박스를 그리고 선택 key 목록만 emit 한다. 다운로드·검색 조건·오류 표시는 기존 `ApplicationStatus.vue` 흐름을 그대로 쓰고 `columns` 인자만 추가한다.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, ant-design-vue 4, Axios(`apiClient`), vue-tsc.

**선행:** S1(백엔드) 완료 — `GET /admin/applications/export/columns`, export `columns` 파라미터, 목록 응답 `finalGraduationDate`.

**설계서:** `docs/superpowers/specs/2026-09-18-application-export-column-selection-design.md` (§6 프론트, §7 오류, §9 결함).

**작업 루트:** 모든 경로는 `recruit_front/` 기준. 검증:

```bash
npm run type-check
```

**커밋:** 사용자가 명시 요청할 때만(`recruit/CLAUDE.md` §6). 커밋 단계 없음.

---

## 파일 구조

| 파일 | 역할 | 변경 |
| --- | --- | --- |
| `src/types/admin/application.ts` | 카탈로그 타입, `finalGraduationDate` | 수정 |
| `src/api/admin/adminApplicationApi.ts` | 카탈로그 조회, 다운로드 `columns` | 수정 |
| `src/views/admin/application/ApplicationExcelColumnModal.vue` | 컬럼 선택 모달 | 신규 |
| `src/views/admin/application/ApplicationStatus.vue` | 모달 연동 + 결함 2건 | 수정 |
| `../api-contract.md` | 🟡 → 🟢 확정 | 수정 |

---

### Task 1: 타입 + API 모듈

**Files:**
- Modify: `src/types/admin/application.ts`
- Modify: `src/api/admin/adminApplicationApi.ts`

- [ ] **Step 1: 타입 추가**

`AdminApplicationSummaryResponse` 의 `finalSchoolName: string` 다음 줄에 추가:

```ts
  /** 최종학력 행의 졸업일(ISO date). 응답 전용 파생 값. 학력이 없으면 null. */
  finalGraduationDate: string | null
```

`AdminApplicationSearchRequest` 인터페이스 아래에 추가:

```ts
/** 지원현황 엑셀 컬럼 1개. key 는 백엔드 ApplicationExportColumn 이름이며 다운로드 요청에 그대로 쓴다. */
export interface ApplicationExportColumnOption {
  key: string
  label: string
  defaultSelected: boolean
}

/** 모달 체크박스 묶음. 그룹·컬럼 순서는 서버 카탈로그 순서 그대로. */
export interface ApplicationExportColumnGroup {
  group: string
  columns: ApplicationExportColumnOption[]
}
```

- [ ] **Step 2: API 수정**

`adminApplicationApi.ts` 상단 type import 목록에 `ApplicationExportColumnGroup` 추가:

```ts
import type {
  AdminApplicationSummaryResponse,
  AdminApplicationSearchRequest,
  AdminApplicationDetailResponse,
  ApplicationExportColumnGroup,
  ApplicationFormLayoutResponse,
} from '@/types/admin/application'
```

`downloadApplicationsExcel` 를 교체하고 그 위에 카탈로그 조회를 추가:

```ts
  // 엑셀 컬럼 카탈로그(모달 체크박스 원천). 항목 정의는 백엔드 enum 이 단일 출처다.
  getApplicationExportColumns() {
    return apiClient.get<ApiResponse<ApplicationExportColumnGroup[]>>('/admin/applications/export/columns')
  },

  // 지원현황 엑셀. 목록 조회와 같은 검색 조건을 그대로 넘겨야 화면과 파일 내용이 일치한다.
  // columns 는 콤마로 이어 보낸다 — axios 기본 배열 직렬화(columns[]=a&columns[]=b)는 서버가 받지 못한다.
  downloadApplicationsExcel(jobPostingId: number, searchRequest: AdminApplicationSearchRequest, columns: string[]) {
    return apiClient.get<Blob>(`/admin/job-postings/${jobPostingId}/applications/export`, {
      params: { ...searchRequest, columns: columns.join(',') },
      responseType: 'blob',
      timeout: PDF_BULK_DOWNLOAD_TIMEOUT_MS,
    })
  },
```

- [ ] **Step 3: 타입 체크 (의도된 실패)**

Run: `npm run type-check`
Expected: `ApplicationStatus.vue` 의 `downloadApplicationsExcel(...)` 인자 부족 오류 1건만. Task 3에서 해소.

---

### Task 2: 컬럼 선택 모달

**Files:**
- Create: `src/views/admin/application/ApplicationExcelColumnModal.vue`

체크박스는 `a-checkbox-group` 을 쓰지 않는다 — 그룹 안에 넣은 "그룹 전체" 체크박스가 그룹 값에 `undefined` 로 섞이기 때문. 개별 `a-checkbox` 의 `checked` 를 `selected` 배열로 직접 제어한다.

- [ ] **Step 1: 컴포넌트 작성**

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { adminApplicationApi } from '@/api/admin/adminApplicationApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { ApplicationExportColumnGroup } from '@/types/admin/application'

const props = defineProps<{
  open: boolean
  /** 본체가 파일을 받는 중. 버튼을 잠근다. */
  downloading: boolean
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 선택한 컬럼 key. 엑셀 컬럼 순서는 서버가 카탈로그 순으로 맞춘다. */
  (event: 'download', columns: string[]): void
}>()

const groups = ref<ApplicationExportColumnGroup[]>([])
const loadingCatalog = ref(false)
const loadFailed = ref(false)
const selected = ref<string[]>([])

const allKeys = computed(() => groups.value.flatMap((group) => group.columns.map((column) => column.key)))
const defaultKeys = computed(() => groups.value.flatMap((group) =>
  group.columns.filter((column) => column.defaultSelected).map((column) => column.key)))

// 카탈로그는 처음 열 때 한 번만 받는다. 실패하면 다음에 열 때 다시 시도한다.
const loadCatalog = async () => {
  if (groups.value.length > 0 || loadingCatalog.value) return
  loadingCatalog.value = true
  loadFailed.value = false
  try {
    const response = await adminApplicationApi.getApplicationExportColumns()
    groups.value = response.data.data
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '엑셀 항목을 불러오지 못했습니다.'))
  } finally {
    loadingCatalog.value = false
  }
}

// 열 때마다 기본 컬럼만 체크된 상태로 시작한다(선택을 기억하지 않는다).
watch(() => props.open, async (open) => {
  if (!open) return
  await loadCatalog()
  selected.value = [...defaultKeys.value]
})

const isSelected = (key: string) => selected.value.includes(key)

const toggleColumn = (key: string, checked: boolean) => {
  selected.value = checked
    ? [...selected.value, key]
    : selected.value.filter((selectedKey) => selectedKey !== key)
}

const groupKeys = (group: ApplicationExportColumnGroup) => group.columns.map((column) => column.key)

const isGroupChecked = (group: ApplicationExportColumnGroup) => groupKeys(group).every(isSelected)

const isGroupIndeterminate = (group: ApplicationExportColumnGroup) => {
  const count = groupKeys(group).filter(isSelected).length
  return count > 0 && count < group.columns.length
}

const toggleGroup = (group: ApplicationExportColumnGroup, checked: boolean) => {
  const keys = groupKeys(group)
  const others = selected.value.filter((key) => !keys.includes(key))
  selected.value = checked ? [...others, ...keys] : others
}

const selectAll = () => { selected.value = [...allKeys.value] }
const clearAll = () => { selected.value = [] }
const resetToDefault = () => { selected.value = [...defaultKeys.value] }

const close = () => emit('update:open', false)

const submit = () => {
  if (selected.value.length === 0) return
  emit('download', [...selected.value])
}
</script>

<template>
  <a-modal
    :open="open"
    title="엑셀 다운로드 항목 선택"
    :width="760"
    :mask-closable="!downloading"
    @cancel="close"
  >
    <a-spin :spinning="loadingCatalog">
      <div class="toolbar">
        <a-button size="small" :disabled="groups.length === 0" @click="selectAll">전체 선택</a-button>
        <a-button size="small" :disabled="groups.length === 0" @click="clearAll">전체 해제</a-button>
        <a-button size="small" :disabled="groups.length === 0" @click="resetToDefault">기본값</a-button>
        <span class="selected-count">{{ selected.length }}개 선택</span>
      </div>

      <a-empty v-if="loadFailed" description="엑셀 항목을 불러오지 못했습니다. 창을 닫고 다시 열어 주세요." />

      <section v-for="group in groups" :key="group.group" class="column-group">
        <a-checkbox
          class="group-title"
          :checked="isGroupChecked(group)"
          :indeterminate="isGroupIndeterminate(group)"
          @change="toggleGroup(group, $event.target.checked)"
        >
          {{ group.group }}
        </a-checkbox>
        <div class="column-list">
          <a-checkbox
            v-for="column in group.columns"
            :key="column.key"
            :checked="isSelected(column.key)"
            @change="toggleColumn(column.key, $event.target.checked)"
          >
            {{ column.label }}
          </a-checkbox>
        </div>
      </section>
    </a-spin>

    <template #footer>
      <a-button :disabled="downloading" @click="close">취소</a-button>
      <a-button
        type="primary"
        :loading="downloading"
        :disabled="selected.length === 0 || groups.length === 0"
        @click="submit"
      >
        다운로드
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.selected-count {
  margin-left: auto;
  color: #536171;
}
.column-group {
  padding: 12px 0;
  border-top: 1px solid #f0f0f0;
}
.group-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.column-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px 12px;
  padding-left: 24px;
}
.column-list :deep(.ant-checkbox-wrapper) {
  margin-inline-start: 0;
}
</style>
```

- [ ] **Step 2: 타입 체크**

Run: `npm run type-check`
Expected: 모달 파일에서 오류 없음(Task 1의 `ApplicationStatus.vue` 오류 1건은 남는다). `$event.target.checked` 타입 오류가 나면 `@change="(event: { target: { checked: boolean } }) => toggleColumn(column.key, event.target.checked)"` 형태로 인자 타입을 명시한다.

---

### Task 3: `ApplicationStatus.vue` 연동 + 결함 2건

**Files:**
- Modify: `src/views/admin/application/ApplicationStatus.vue`

- [ ] **Step 1: 결함 §9.1 — 철회 라벨**

47행:

```ts
  WITHDRAWN: '지원 철회',
```

- [ ] **Step 2: 결함 §9.2 — 졸업년월 컬럼**

95행 컬럼 정의 교체:

```ts
  { title: '졸업년월', dataIndex: 'finalGraduationDate', key: 'finalGraduationDate' },
```

bodyCell 의 `withdrawnAt` 분기(468~470행)를 교체 — 이 변경으로 `withdrawnAt` 키 컬럼이 없어져 기존 분기는 쓰이지 않는다:

```vue
              <template v-else-if="column.key === 'finalGraduationDate'">
                {{ formatDate(record.finalGraduationDate, 'YYYY-MM') }}
              </template>
```

- [ ] **Step 3: 모달 연동 — script**

import 추가(다른 컴포넌트 import 옆):

```ts
import ApplicationExcelColumnModal from './ApplicationExcelColumnModal.vue'
```

288~302행 `downloadExcel` 블록 교체:

```ts
// 엑셀 버튼은 항목 선택 모달을 연다. 모달에서 고른 컬럼으로, 화면에 걸어둔 검색 조건 그대로 받는다
// (목록과 같은 조건이라 보이는 결과와 일치한다).
const excelModalOpen = ref(false)
const downloadingExcel = ref(false)
const downloadExcel = async (columns: string[]) => {
  if (downloadingExcel.value || selectedJobPostingId.value === null) return

  downloadingExcel.value = true
  try {
    const response = await adminApplicationApi.downloadApplicationsExcel(selectedJobPostingId.value, searchRequest, columns)
    saveBlobResponse(response, '지원현황.xlsx')
    excelModalOpen.value = false
  } catch (error) {
    message.error(await getBlobErrorMessage(error, '지원현황 엑셀을 내려받지 못했습니다.'))
  } finally {
    downloadingExcel.value = false
  }
}
```

- [ ] **Step 4: 모달 연동 — template**

449~451행 엑셀 버튼 교체(로딩 표시는 모달 다운로드 버튼이 담당):

```vue
          <a-button :disabled="selectedJobPostingId === null" @click="excelModalOpen = true">
            <FileExcelOutlined />엑셀 다운로드
          </a-button>
```

최상위 `<div class="job-posting-form">` 의 닫는 태그 바로 앞(`</a-spin>` 다음)에 추가:

```vue
    <ApplicationExcelColumnModal
      v-model:open="excelModalOpen"
      :downloading="downloadingExcel"
      @download="downloadExcel"
    />
```

- [ ] **Step 5: 타입 체크**

Run: `npm run type-check`
Expected: 오류 0건

- [ ] **Step 6: 빌드**

Run: `npm run build`
Expected: 성공

---

### Task 4: 화면 검증 + 계약 확정 + 보고

- [ ] **Step 1: 브라우저 확인 (백엔드·프론트 dev 서버 실행 상태)**

`preview_start` 로 프론트를 띄우고 관리자 지원현황 조회에서 확인한다:
1. "엑셀 다운로드" → 모달이 열리고 6개 그룹, 기본 14개 체크.
2. 그룹 체크박스: 일부 선택 시 중간 상태, 클릭 시 그룹 전체 선택/해제.
3. 전체 해제 → 다운로드 버튼 비활성. 기본값 → 14개로 복귀.
4. 몇 개 선택 후 다운로드 → 파일 저장, 모달 닫힘. 다시 열면 기본 14개로 초기화.
5. 그리드 "졸업년월" 이 `YYYY-MM` 졸업일로 표시(철회일시 아님).
6. 받은 xlsx 를 Excel 로 열어 헤더가 한글·카탈로그 순서인지, 줄바꿈 셀(경력 등)이 여러 줄로 보이고 행 높이가 맞춰지는지 확인(설계 §8 수동 확인 항목). 행 높이가 안 맞으면 보고서 미결 사항에 기록.

로그인·백엔드 기동이 막혀 브라우저 확인을 못 하면 그 사실과 이유를 보고에 명시한다.

- [ ] **Step 2: `api-contract.md` 🟢 확정**

`recruit/api-contract.md` 지원현황 조회 섹션에서:
- 목록 응답의 `🟡 finalGraduationDate` 줄 → `🟢`, 날짜를 구현 완료일로.
- export 섹션의 `🟡 컬럼 선택 확장 초안` → `🟢 컬럼 선택 확장 완료 (YYYY-MM-DD)`. 구현과 다른 점이 있으면 문구를 코드에 맞춘다(면제 사유·자격증번호 제외, 표기 PDF 기준 포함).
- `GET /admin/applications/export/columns` 헤더 `🟡 초안` → `🟢 구현 완료`.

- [ ] **Step 3: HTML 구현 리포트**

`design-report` 스킬로 `docs/archive/reports/admin-application-export-columns_implementation.html` 을 만든다(S1+S2 통합 1건). 설계 리포트 `admin-application-export-columns_design.html` 은 구현과 달라진 점이 있을 때만 제자리 갱신.

- [ ] **Step 4: 최종 보고**

양쪽 변경 파일 / 테스트·type-check·build 결과 / 계약 변경분 / 남은 이슈(수동 확인 결과 포함)를 요약한다.
