<template>
  <div class="question-tab">
    <a-alert
      v-if="liveCount > 0 && supplement.questions.length > 0"
      type="warning"
      show-icon
      class="tab-alert"
      :message="`지금 입력 시간 중인 지원자가 ${liveCount}명 있습니다. 질문을 고치면 이미 작성된 답변과 질문이 어긋날 수 있습니다.`"
    />
    <a-alert
      v-if="supplement.questions.length === 0"
      type="warning"
      show-icon
      class="tab-alert"
      message="등록된 질문이 없습니다. 질문을 1개 이상 등록해야 지원자에게 입력 버튼이 보입니다."
    />
    <p v-if="supplement.questions.length > 1" class="hint"><HolderOutlined /> 끌어서 순서 변경 · 이 순서로 지원자에게 보입니다</p>

    <ol class="question-list" @dragover="onDragOver" @drop="onDrop">
      <li
        v-for="(question, index) in supplement.questions"
        :key="question.questionId"
        class="question-item"
        :class="{
          editing: editingId === question.questionId,
          dragging: dragFrom === index,
          'drop-before': dropIndex === index && dragFrom !== null,
          'drop-after': dropIndex === supplement.questions.length && index === supplement.questions.length - 1 && dragFrom !== null,
        }"
        :data-index="index"
        :draggable="grabbedIndex === index"
        @dragstart="onDragStart($event, index)"
        @dragend="onDragEnd"
      >
        <span
          class="grip"
          :class="{ off: editingId !== null || busy }"
          title="끌어서 순서 변경"
          @mousedown="grab(index)"
          @mouseup="dragFrom === null && (grabbedIndex = null)"
        ><HolderOutlined /></span>
        <span class="no">{{ index + 1 }}</span>
        <div class="body">
          <template v-if="editingId === question.questionId">
            <a-textarea v-model:value="editingText" :maxlength="MAX_LENGTH" :auto-size="{ minRows: 2, maxRows: 6 }" show-count />
            <div class="edit-actions">
              <a-button size="small" @click="cancelEdit">취소</a-button>
              <a-button size="small" type="primary" :loading="busy" @click="saveEdit(question.questionId)">저장</a-button>
            </div>
          </template>
          <div v-else class="text">{{ question.content }}</div>
        </div>
        <div v-if="editingId !== question.questionId" class="actions">
          <a-button type="text" size="small" title="수정" :disabled="busy" @click="startEdit(question.questionId, question.content)"><EditOutlined /></a-button>
          <a-button type="text" size="small" danger title="삭제" :disabled="busy" @click="confirmDelete(question.questionId, index)"><DeleteOutlined /></a-button>
        </div>
      </li>
    </ol>

    <div class="add-row">
      <a-textarea
        v-model:value="newText"
        :maxlength="MAX_LENGTH"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        placeholder="새 질문을 입력하세요 (최대 500자)"
      />
      <a-button type="primary" :loading="busy" @click="addQuestion"><PlusOutlined />추가</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { adminInterviewSupplementApi } from '@/api/admin/adminInterviewSupplementApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminInterviewSupplement } from '@/types/admin/interviewSupplement'

/* 백엔드 InterviewSupplementQuestionSaveRequest @Size(max = 500) */
const MAX_LENGTH = 500

const props = defineProps<{ stageId: number; supplement: AdminInterviewSupplement; liveCount: number }>()
const emit = defineEmits<{ (e: 'update', value: AdminInterviewSupplement): void }>()

const busy = ref(false)
const newText = ref('')
const editingId = ref<number | null>(null)
const editingText = ref('')

const run = async (request: () => Promise<{ data: { data: AdminInterviewSupplement } }>, fallback: string, done?: string) => {
  busy.value = true
  try {
    const response = await request()
    emit('update', response.data.data)
    if (done) message.success(done)
    return true
  } catch (error) {
    message.error(getApiErrorMessage(error, fallback))
    return false
  } finally {
    busy.value = false
  }
}

const addQuestion = async (): Promise<void> => {
  const content = newText.value.trim()
  if (!content) {
    message.warning('질문을 입력하세요.')
    return
  }
  if (await run(() => adminInterviewSupplementApi.addQuestion(props.stageId, content), '질문을 추가하지 못했습니다.', '질문을 추가했습니다.')) {
    newText.value = ''
  }
}

const startEdit = (questionId: number, content: string): void => {
  editingId.value = questionId
  editingText.value = content
}

const cancelEdit = (): void => {
  editingId.value = null
}

const saveEdit = async (questionId: number): Promise<void> => {
  const content = editingText.value.trim()
  if (!content) {
    message.warning('질문을 입력하세요.')
    return
  }
  if (await run(() => adminInterviewSupplementApi.updateQuestion(props.stageId, questionId, content), '질문을 수정하지 못했습니다.', '질문을 수정했습니다.')) {
    editingId.value = null
  }
}

const confirmDelete = (questionId: number, index: number): void => {
  Modal.confirm({
    title: `질문 ${index + 1} 삭제`,
    content: '이미 답변이 달린 질문은 삭제할 수 없습니다. 삭제하면 남은 질문의 번호가 다시 매겨집니다.',
    okText: '삭제',
    okType: 'danger',
    cancelText: '취소',
    onOk: () => run(() => adminInterviewSupplementApi.deleteQuestion(props.stageId, questionId), '질문을 삭제하지 못했습니다.', '질문을 삭제했습니다.'),
  })
}

/* 드래그 순서 변경: 손잡이를 잡았을 때만 끌 수 있다(문장 드래그 선택과 섞이지 않게). */
const grabbedIndex = ref<number | null>(null)
const dragFrom = ref<number | null>(null)
const dropIndex = ref<number | null>(null)

const grab = (index: number): void => {
  if (editingId.value === null && !busy.value) grabbedIndex.value = index
}

const onDragStart = (event: DragEvent, index: number): void => {
  if (grabbedIndex.value !== index) return
  dragFrom.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const onDragOver = (event: DragEvent): void => {
  if (dragFrom.value === null) return
  const item = (event.target as HTMLElement).closest<HTMLElement>('.question-item')
  if (!item) return
  event.preventDefault()
  const rect = item.getBoundingClientRect()
  const index = Number(item.dataset.index)
  dropIndex.value = event.clientY > rect.top + rect.height / 2 ? index + 1 : index
}

const onDrop = async (event: DragEvent): Promise<void> => {
  if (dragFrom.value === null || dropIndex.value === null) return
  event.preventDefault()
  const from = dragFrom.value
  const to = dropIndex.value > from ? dropIndex.value - 1 : dropIndex.value
  onDragEnd()
  if (to === from) return
  const ids = props.supplement.questions.map((question) => question.questionId)
  const [moved] = ids.splice(from, 1)
  if (moved === undefined) return
  ids.splice(to, 0, moved)
  await run(() => adminInterviewSupplementApi.reorderQuestions(props.stageId, ids), '순서를 바꾸지 못했습니다.', `질문 ${from + 1} → ${to + 1}번으로 옮겼습니다.`)
}

const onDragEnd = (): void => {
  grabbedIndex.value = null
  dragFrom.value = null
  dropIndex.value = null
}
</script>

<style scoped>
.question-tab {
  max-width: 920px;
}
.tab-alert {
  margin-bottom: 12px;
}
.hint {
  font-size: 12.5px;
  color: var(--app-text-secondary);
  margin: 0 0 10px;
}
.question-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.question-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 10px 10px 8px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  background: #fff;
}
.question-item.editing {
  border-color: var(--app-color-primary);
}
.question-item.dragging {
  opacity: 0.4;
  border-style: dashed;
}
.question-item.drop-before::before,
.question-item.drop-after::after {
  content: '';
  position: absolute;
  left: -1px;
  right: -1px;
  height: 3px;
  border-radius: 2px;
  background: var(--app-color-primary);
}
.question-item.drop-before::before {
  top: -6px;
}
.question-item.drop-after::after {
  bottom: -6px;
}
.grip {
  width: 18px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-muted);
  cursor: grab;
  flex: none;
}
.grip.off {
  cursor: default;
  opacity: 0.4;
}
.no {
  flex: none;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--app-bg-selected);
  color: var(--app-color-primary);
  font-weight: 700;
  font-size: 12.5px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.body {
  flex: 1;
  min-width: 0;
}
.text {
  white-space: pre-wrap;
  word-break: keep-all;
  line-height: 1.6;
  padding-top: 2px;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 22px;
}
.actions {
  display: flex;
  flex: none;
}
.add-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 10px;
  padding: 10px;
  border: 1px dashed var(--app-border-strong);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-muted);
}
</style>
