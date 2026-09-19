<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MailOutlined, MessageOutlined, PlusOutlined, StarFilled } from '@ant-design/icons-vue'

import { messageApi } from '@/api/admin/messageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type {
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import { MESSAGE_TYPES, messageTypeLabel } from './messageTypes'
import { smsByteLength, smsKindOf } from './messageRender'
import { FIELD_MAX_LENGTH, useVariableCursor } from './useVariableCursor'

const NAME_MAX_LENGTH = 100

interface TemplateForm {
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string
  mailBody: string
  smsBody: string
}

const templates = ref<MessageTemplate[]>([])
const variables = ref<MessageVariable[]>([])
const loading = ref(false)
const saving = ref(false)

const keyword = ref('')
const typeFilter = ref<MessageType | 'ALL'>('ALL')
const selectedId = ref<number | null>(null)

const emptyForm = (type: MessageType): TemplateForm => ({
  type,
  name: '',
  defaultTemplate: false,
  mailSubject: '',
  mailBody: '',
  smsBody: '',
})

const form = reactive<TemplateForm>(emptyForm('RESULT_ANNOUNCEMENT'))

const { resetCursor, rememberCursor, insertVariable } = useVariableCursor(
  (field) => form[field],
  (field, value) => {
    form[field] = value
  },
)

const typeOptions = MESSAGE_TYPES.map((meta) => ({ value: meta.type, label: `${meta.group} · ${meta.name}` }))
const typeFilterOptions = [{ value: 'ALL', label: '전체 종류' }, ...typeOptions]

const groups = computed(() => {
  const word = keyword.value.trim()
  return MESSAGE_TYPES.filter((meta) => typeFilter.value === 'ALL' || meta.type === typeFilter.value).map(
    (meta) => ({
      meta,
      items: templates.value.filter((template) => template.type === meta.type && (!word || template.name.includes(word))),
    }),
  )
})

const selectedTemplate = computed<MessageTemplate | undefined>(() =>
  templates.value.find((template) => template.id === selectedId.value),
)

const availableVariables = computed(() => variables.value.filter((variable) => variable.types.includes(form.type)))

const smsBytes = computed(() => smsByteLength(form.smsBody))
const smsKindText = computed(() => {
  const kind = smsKindOf(smsBytes.value)
  return kind === null ? '2000byte 초과' : `${kind} 예상`
})

/* ---------- 조회 ---------- */

const loadTemplates = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await messageApi.getTemplates()
    templates.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const loadVariables = async (): Promise<void> => {
  try {
    const response = await messageApi.getVariables()
    variables.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '변수 목록을 불러오지 못했습니다.'))
  }
}

/* ---------- 선택·편집 ---------- */

const selectTemplate = (template: MessageTemplate): void => {
  selectedId.value = template.id
  Object.assign(form, {
    type: template.type,
    name: template.name,
    defaultTemplate: template.defaultTemplate,
    mailSubject: template.mailSubject ?? '',
    mailBody: template.mailBody ?? '',
    smsBody: template.smsBody ?? '',
  })
  resetCursor()
}

const startNew = (): void => {
  selectedId.value = null
  Object.assign(form, emptyForm(typeFilter.value === 'ALL' ? 'RESULT_ANNOUNCEMENT' : typeFilter.value))
  resetCursor()
}

const duplicate = (): void => {
  selectedId.value = null
  const suffix = ' (복사본)'
  form.name = form.name.slice(0, NAME_MAX_LENGTH - suffix.length) + suffix
  form.defaultTemplate = false
}

/* ---------- 저장·삭제 ---------- */

const blankToNull = (value: string): string | null => (value.trim() ? value : null)

const toRequest = (): MessageTemplateSaveRequest => ({
  type: form.type,
  name: form.name.trim(),
  defaultTemplate: form.defaultTemplate,
  mailSubject: blankToNull(form.mailSubject),
  mailBody: blankToNull(form.mailBody),
  smsBody: blankToNull(form.smsBody),
})

const save = async (): Promise<void> => {
  if (!form.name.trim()) {
    message.warning('템플릿 이름을 입력해 주세요.')
    return
  }

  const targetId = selectedId.value
  saving.value = true
  try {
    const request = toRequest()
    const response =
      targetId === null
        ? await messageApi.createTemplate(request)
        : await messageApi.updateTemplate(targetId, request)
    message.success('템플릿을 저장했습니다.')
    await loadTemplates()
    if (selectedId.value === targetId) {
      selectTemplate(response.data.data)
    }
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

const remove = (): void => {
  const target = selectedTemplate.value
  if (!target) {
    return
  }

  Modal.confirm({
    title: '템플릿을 삭제할까요?',
    content: `"${target.name}" 템플릿을 삭제합니다. 발송 이력에는 영향이 없습니다.`,
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: async () => {
      try {
        await messageApi.deleteTemplate(target.id)
        message.success('템플릿을 삭제했습니다.')
        await loadTemplates()
        const first = groups.value.flatMap((group) => group.items)[0]
        if (first) {
          selectTemplate(first)
        } else {
          startNew()
        }
      } catch (error) {
        message.error(getApiErrorMessage(error, '템플릿을 삭제하지 못했습니다.'))
      }
    },
  })
}

onMounted(async () => {
  await Promise.all([loadVariables(), loadTemplates()])
  const first = groups.value.flatMap((group) => group.items)[0]
  if (first) {
    selectTemplate(first)
  }
})
</script>

<template>
  <div class="message-template-view">
    <header class="page-header">
      <div>
        <h1 class="page-title">메시지 템플릿</h1>
        <p class="page-desc">
          종류별로 메일·SMS 문구를 만들어 둡니다. 종류마다 기본 템플릿 1개는 발송 화면에서 자동으로 불러옵니다.
        </p>
      </div>
      <a-button type="primary" @click="startNew"><PlusOutlined /> 새 템플릿</a-button>
    </header>

    <div class="manage-body">
      <!-- 좌: 종류별 목록 -->
      <section class="panel list-panel">
        <div class="list-filter">
          <a-input v-model:value="keyword" placeholder="템플릿 이름 검색" allow-clear />
          <a-select popup-class-name="message-select-dropdown" v-model:value="typeFilter" class="type-filter" :options="typeFilterOptions" />
        </div>

        <a-spin :spinning="loading">
          <div v-for="group in groups" :key="group.meta.type" class="template-group">
            <p class="group-label">
              <span>{{ group.meta.group }} · {{ group.meta.name }}</span>
              <span>{{ group.items.length }}</span>
            </p>
            <button
              v-for="template in group.items"
              :key="template.id"
              type="button"
              class="template-item"
              :class="{ selected: template.id === selectedId }"
              @click="selectTemplate(template)"
            >
              <span class="item-name">
                <StarFilled v-if="template.defaultTemplate" class="default-star" />
                {{ template.name }}
                <a-tag v-if="template.defaultTemplate" color="green" class="default-tag">기본</a-tag>
              </span>
              <span class="item-sub">{{ template.mailSubject ?? template.smsBody }}</span>
            </button>
          </div>
        </a-spin>
      </section>

      <!-- 우: 편집기 -->
      <section class="panel">
        <div class="panel-header">
          <div>
            <span class="panel-title">{{ selectedTemplate?.name ?? '새 템플릿' }}</span>
            <p class="panel-meta">
              {{ messageTypeLabel(form.type) }}<template v-if="selectedTemplate?.defaultTemplate"> · 기본 템플릿</template>
            </p>
          </div>
          <a-space>
            <a-button v-if="selectedId !== null" danger @click="remove">삭제</a-button>
            <a-button v-if="selectedId !== null" @click="duplicate">복제</a-button>
            <a-button type="primary" :loading="saving" @click="save">저장</a-button>
          </a-space>
        </div>

        <div class="editor-body">
          <div class="form-row">
            <div class="field">
              <span class="field-label">템플릿 이름</span>
              <a-input v-model:value="form.name" :maxlength="NAME_MAX_LENGTH" />
            </div>
            <div class="field">
              <span class="field-label">메시지 종류</span>
              <a-select popup-class-name="message-select-dropdown" v-model:value="form.type" :options="typeOptions" />
            </div>
          </div>
          <a-checkbox v-model:checked="form.defaultTemplate">
            이 종류의 기본 템플릿으로 사용 <span class="hint">(기존 기본 템플릿은 해제됨)</span>
          </a-checkbox>

          <h3 class="section-title"><MailOutlined /> 메일</h3>
          <div class="field">
            <span class="field-label">제목</span>
            <a-input
              v-model:value="form.mailSubject"
              :maxlength="FIELD_MAX_LENGTH.mailSubject"
              @blur="rememberCursor('mailSubject', $event)"
            />
          </div>
          <div class="field">
            <span class="field-label">본문</span>
            <a-textarea
              v-model:value="form.mailBody"
              :rows="10"
              :maxlength="FIELD_MAX_LENGTH.mailBody"
              @blur="rememberCursor('mailBody', $event)"
            />
          </div>

          <h3 class="section-title">
            <MessageOutlined /> SMS <span class="hint">변수 치환 전 {{ smsBytes }}byte · {{ smsKindText }}</span>
          </h3>
          <a-textarea
            v-model:value="form.smsBody"
            :rows="5"
            :maxlength="FIELD_MAX_LENGTH.smsBody"
            @blur="rememberCursor('smsBody', $event)"
          />

          <div class="variables">
            <span class="variables-label">이 종류에서 쓸 수 있는 변수</span>
            <a-tooltip v-for="variable in availableVariables" :key="variable.key" :title="variable.label">
              <button type="button" class="variable-chip" @click="insertVariable(variable.key)">
                {{ variable.key }}
              </button>
            </a-tooltip>
          </div>
        </div>

        <div class="panel-footer">
          <span>메일·SMS 중 하나만 채워도 저장할 수 있습니다. 메일은 제목과 본문을 함께 입력합니다.</span>
          <span v-if="selectedTemplate">마지막 수정 {{ formatDate(selectedTemplate.updatedAt, 'YYYY-MM-DD HH:mm') }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 18px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 700;
}

.page-desc {
  margin: 0;
  color: var(--app-text-secondary);
}

.manage-body {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  box-shadow: var(--app-shadow-soft);
}

.list-filter {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--app-border-default);
}

.type-filter {
  width: 140px;
  flex: none;
}

.template-group {
  padding-bottom: 4px;
}

.group-label {
  display: flex;
  justify-content: space-between;
  margin: 0;
  padding: 10px 14px 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.template-item {
  display: block;
  width: 100%;
  padding: 10px 14px;
  border: 0;
  border-left: 3px solid transparent;
  background: none;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-muted);
  }

  &.selected {
    background: var(--app-bg-selected);
    border-left-color: var(--app-color-primary);
  }
}

.item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.default-star {
  color: #d4a017;
}

.default-tag {
  margin: 0;
}

.item-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--app-border-default);
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
}

.panel-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.editor-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-secondary);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--app-border-default);
  font-size: 14px;
  font-weight: 600;
}

.hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-muted);
}

.variables {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border: 1px dashed var(--app-border-strong);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-muted);
}

.variables-label {
  margin-right: 2px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.variable-chip {
  height: 24px;
  padding: 0 10px;
  border: 1px solid #cfe0c6;
  border-radius: 14px;
  background: var(--app-bg-surface);
  color: var(--app-color-primary);
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: var(--app-bg-selected);
  }
}

.panel-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-top: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  font-size: 12px;
  color: var(--app-text-secondary);
}
</style>
