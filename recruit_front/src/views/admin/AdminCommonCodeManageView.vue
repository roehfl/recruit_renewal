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
const groupRows = computed(() => codes.value.filter((item) => item.groupCode === GROUP_OF_GROUPS))

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
        !kw || item.code.toLowerCase().includes(kw) || item.displayName.toLowerCase().includes(kw),
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

  const creating = editingId.value === null

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
    if (creating) {
      selectedGroup.value = groupCode
    }
    await loadCodes()
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
        지원서·관리자 화면의 선택지를 관리합니다. 그룹 자체는 <code>CODE_GROUP</code> 그룹에서
        관리하며, 삭제 대신 비활성화만 제공합니다.
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
          <a-input
            v-model:value="keyword"
            class="keyword-input"
            placeholder="코드 · 표시명 검색"
            allow-clear
          />
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
