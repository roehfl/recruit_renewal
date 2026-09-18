<script setup lang="ts">
import AddQuestionBody from './AddQuestionBody.vue'
import type { QuestionForm } from '@/types/question'

const emit = defineEmits<{
  (e: 'update:opne', value: boolean): void 
  (e: 'success', data: QuestionForm): void
}>()

/* 저장 결과를 모르는 채 닫지 않는다. 부모가 저장에 성공하면 닫는다. */
const handleSubmit = (data: QuestionForm) => {
  emit('success', data)
}
const handleCancel = () => {
  emit('update:opne', false)
}
</script>

<template>
  <div class="dm-modal">
    <div class="dm-header">
      <h2 class="dm-title">질문 추가</h2>
      <button class="dm-close" @click="handleCancel">✕</button>
    </div>
    <div class="dm-scroll dm-body">
      <AddQuestionBody
        submit-label="저장"
        @submit="handleSubmit"
        @cancel="handleCancel"
      />
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
  flex-direction: row-reverse;
  align-items: center;
  padding: 8px 40px 20px;
}

.dm-modal {
  font-family: 'Noto Sans KR', 'Noto Sans CJK KR', 'Apple SD Gothic Neo', sans-serif;
  color: #1f2937;
}

.dm-header {
  position: relative;
  padding: 32px 40px 24px;
  border-bottom: 1px solid #eef1ee;
}

.dm-eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.dm-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #2f6f55;
}

.dm-eyebrow-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #2f6f55;
  text-transform: uppercase;
}

.dm-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
}

.dm-close {
  position: absolute;
  top: 28px;
  right: 32px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: #f5f7fa;
  color: #6b7280;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.dm-close:hover {
  background: #eaf8f3;
  color: #0f4726;
}

.dm-body {
  max-height: 560px;
  overflow-y: auto;
  padding: 28px 40px 36px;
}

.dm-scroll::-webkit-scrollbar {
  width: 12px;
}

.dm-scroll::-webkit-scrollbar-track {
  background: #f9f9f9;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb {
  border: 3px solid #f9f9f9;
  background: #bfbfbf75;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}
</style>
