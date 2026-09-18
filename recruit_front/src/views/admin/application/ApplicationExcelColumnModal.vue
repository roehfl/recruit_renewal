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
    // 로딩 중에 닫았다 다시 열었어도 카탈로그가 도착한 시점에 기본 컬럼을 채운다.
    selected.value = [...defaultKeys.value]
  } catch (error) {
    loadFailed.value = true
    message.error(getApiErrorMessage(error, '엑셀 항목을 불러오지 못했습니다.'))
  } finally {
    loadingCatalog.value = false
  }
}

// 열 때마다 기본 컬럼만 체크된 상태로 시작한다(선택을 기억하지 않는다).
watch(() => props.open, (open) => {
  if (!open) return
  selected.value = [...defaultKeys.value]
  void loadCatalog()
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
    :closable="!downloading"
    :keyboard="!downloading"
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
  color: var(--app-text-secondary);
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
