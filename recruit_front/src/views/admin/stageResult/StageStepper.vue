<script setup lang="ts">
import { STAGE_STATUS_LABELS, type StageListItem } from '@/types/admin/stage'
import { formatDate } from '@/common/dateUtil'

defineProps<{
  stages: StageListItem[]
  selectedStageId: number | null
}>()

const emit = defineEmits<{
  (event: 'select', stageId: number): void
  (event: 'open-config'): void
}>()

/** 부제: 상태 라벨 + 발표일(있을 때). 대상 수는 단계 목록 API 에 없어 넣지 않는다. */
const subtitleOf = (stage: StageListItem): string => {
  const label = STAGE_STATUS_LABELS[stage.status]
  if (stage.resultAnnouncementDateTime === null) {
    return label
  }
  const date = formatDate(stage.resultAnnouncementDateTime, 'MM-DD')
  return stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'
    ? `${label} · ${date}`
    : `${label} · ${date} 발표`
}

const isDone = (stage: StageListItem) => stage.status === 'RESULT_ANNOUNCED' || stage.status === 'CLOSED'
</script>

<template>
  <nav class="stage-stepper" aria-label="전형 단계">
    <button
      v-for="(stage, index) in stages"
      :key="stage.id"
      type="button"
      class="step"
      :class="{ active: stage.id === selectedStageId, done: isDone(stage) }"
      :aria-current="stage.id === selectedStageId ? 'step' : undefined"
      @click="emit('select', stage.id)"
    >
      <!-- 번호는 배열 순서다. stageOrder 는 10 단위로 부여될 수 있어 그대로 쓰면 "10, 20"이 된다. -->
      <span class="step-no">{{ isDone(stage) ? '✓' : index + 1 }}</span>
      <span class="step-body">
        <span class="step-name">{{ stage.stageName }}</span>
        <span class="step-sub">{{ subtitleOf(stage) }}</span>
      </span>
    </button>
    <button type="button" class="step-config" @click="emit('open-config')">단계 설정</button>
  </nav>
</template>

<style scoped lang="scss">
.stage-stepper {
  display: flex;
  overflow-x: auto;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  margin-bottom: 12px;
}

.step {
  flex: 1 0 auto;
  min-width: 180px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  border-right: 1px solid var(--app-border-default);
  background: transparent;
  cursor: pointer;
  text-align: left;

  &:last-child {
    border-right: none;
  }

  &:hover {
    background: var(--app-bg-selected);
  }

  &.active {
    background: var(--app-bg-selected);
    box-shadow: inset 0 -3px 0 var(--app-color-primary);
  }
}

.step-no {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--app-border-strong);
  border-radius: 50%;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--app-text-muted);
}

.step.done .step-no {
  background: #e8f4ec;
  border-color: #a8d5b8;
  color: var(--app-color-success);
}

.step.active .step-no {
  background: var(--app-color-primary);
  border-color: var(--app-color-primary);
  color: #fff;
}

.step-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.step-name {
  font-size: 13px;
  font-weight: 650;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step.active .step-name {
  color: var(--app-color-primary);
}

.step-sub {
  font-size: 11px;
  color: var(--app-text-muted);
  white-space: nowrap;
}

.step-config {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: none;
  border-left: 1px solid var(--app-border-default);
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  white-space: nowrap;

  &:hover {
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
  }
}
</style>
