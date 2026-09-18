<script setup lang="ts">
import { ref, watch } from 'vue'
import type { QuestionForm } from '@/types/question';

const props = withDefaults(defineProps<{
  initialValues?: QuestionForm | null
  submitLabel?: string
  showCancel?: boolean
}>(), {
  initialValues: null,
  submitLabel: '저장',
  showCancel: false,
})

const emit = defineEmits<{
  (e: 'submit', data: QuestionForm): void
  (e: 'cancel'): void
}>()

const createEmptyForm = (): QuestionForm => ({
  category: null,
  required: null,
  answerType: null,
  maxLength: null,
  minLength: null,
  questionText: null,
  helperText: null
})

const editForm = ref<QuestionForm>(createEmptyForm())

watch(() => props.initialValues, (newVal) => {
  editForm.value = newVal ? { ...newVal } : createEmptyForm()
}, { immediate: true })

/* a-input type=number 는 문자열을 돌려준다. 비교·전송 전에 숫자로 바꾼다(빈 값은 null). */
const toNumber = (value: unknown): number | null =>
  value === null || value === undefined || value === '' ? null : Number(value)

/* 백엔드 JobPostingQuestionService.validateLength 와 같은 유형별 상한. */
const TYPE_MAX_LENGTH: Record<string, { label: string; max: number }> = {
  SHORT_TEXT: { label: '단답형', max: 500 },
  LONG_TEXT: { label: '서술형', max: 5000 },
}

const validation = (maxLength: number | null, minLength: number | null): boolean => {
  if(!editForm.value?.questionText?.trim() || maxLength === null || !editForm.value.category?.trim() ||!(editForm.value.required !== null) || !editForm.value.answerType?.trim()) {
    alert("필수 값을 모두 입력해주세요.")
    return false
  }
  if (!Number.isInteger(maxLength) || maxLength <= 0) {
    alert("최대 글자 수는 0보다 큰 정수로 입력해주세요.")
    return false
  }
  if (minLength !== null && (!Number.isInteger(minLength) || minLength < 0)) {
    alert("최소 글자 수는 0 이상의 정수로 입력해주세요.")
    return false
  }
  if (minLength !== null && minLength > maxLength) {
    alert("최소 글자 수는 최대 글자 수보다 클 수 없습니다.")
    return false
  }
  const typeLimit = TYPE_MAX_LENGTH[editForm.value.answerType ?? '']
  if (typeLimit && maxLength > typeLimit.max) {
    alert(`${typeLimit.label} 최대 글자 수는 ${typeLimit.max}자 이하로 입력해주세요.`)
    return false
  }
  return true
}

/* 저장 성공 전에는 폼을 비우지 않는다. 실패하면 입력을 고쳐 다시 저장할 수 있어야 한다(부모가 성공 시 새로 만든다). */
const handleSubmit = (): void => {
  const maxLength = toNumber(editForm.value.maxLength)
  const minLength = toNumber(editForm.value.minLength)
  if (validation(maxLength, minLength)) {
    emit('submit', { ...editForm.value, maxLength, minLength })
  }
}

const handleCancel = (): void => {
  editForm.value = createEmptyForm()
  emit('cancel')
}
</script>

<template>
  <div>
    <div class="option-area">
      <div class="field-radio">
        <span>질문 유형<em> *</em></span>
        <a-radio-group v-model:value="editForm.category" button-style="solid">
          <a-radio-button value='SELF_INTRODUCTION'>자기소개</a-radio-button>
          <a-radio-button value='GENERAL'>기본질문</a-radio-button>
          <a-radio-button value='JOB_SPECIFIC'>직무질문</a-radio-button>
          <a-radio-button value='ETC'>기타</a-radio-button>
        </a-radio-group>
      </div>

      <div class="field-radio">
        <span>필수 여부<em> *</em></span>
        <a-radio-group v-model:value="editForm.required" button-style="solid">
          <a-radio-button :value="true">필수</a-radio-button>
          <a-radio-button :value="false">선택</a-radio-button>
        </a-radio-group>
      </div>

      <div class="field-radio">
        <span>답변 길이<em> *</em></span>
        <a-radio-group v-model:value="editForm.answerType" button-style="solid">
          <a-radio-button value='SHORT_TEXT'>단답형</a-radio-button>
          <a-radio-button value='LONG_TEXT'>서술형</a-radio-button>
        </a-radio-group>
      </div>
    </div>

    <div class="input-area">
      <div class="field-label">
        <span>최대 글자 수<em> *</em></span>
        <a-input v-model:value="editForm.maxLength" type="number"></a-input>
      </div>

      <div class="field-label">
        <span>최소 글자 수</span>
        <a-input v-model:value="editForm.minLength" type="number"></a-input>
      </div>
    </div>

    <div class="input-area">
      <div class="field">
        <span>질문<em> *</em></span>
        <a-textarea v-model:value="editForm.questionText" placeholder="질문을 입력하세요" />
      </div>
    </div>

    <div class="input-area">
      <label class="field">
        <span>설명</span>
        <a-textarea v-model:value="editForm.helperText" placeholder="설명을 입력하세요" />
      </label>
    </div>

    <div class="detail-foot">
      <a-button
        v-if="showCancel"
        @click="handleCancel">
        취소
      </a-button>
      <a-button
        type="primary"
        @click="handleSubmit">
        {{ submitLabel }}
      </a-button>
    </div>
  </div>
</template>

<style scoped>

em {
  color: #ff4d4f;
  font-style: normal;
}

.input-area {
  display: flex;
  width: 100%;
}

.option-area {
  display: flex;
  width: 100%;
  margin-top: 20px;
  margin-bottom: 20px;
  gap: 6px;
  min-width: 0;
}

.field {
  display: flex;
  width: 100%;
  margin-right: 15px;
  margin-bottom: 20px;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field-label {
  margin-right: 15px;
  margin-bottom: 20px;
  gap: 6px;
  min-width: 0;
}

.field-radio {
  display: flex;
  flex-direction: column;
  margin-right: 15px;
  gap: 6px;
  min-width: 0;
}

.detail-foot {
  flex: none;
  padding: 12px 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>