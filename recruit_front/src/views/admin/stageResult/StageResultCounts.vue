<script setup lang="ts">
import type { AdminStageResult, StageResultStatus } from '@/types/admin/stage'

const props = defineProps<{
  results: AdminStageResult[]
  /** 현재 적용된 결과 필터. null 이면 전체 */
  activeStatus: StageResultStatus | null
}>()

const emit = defineEmits<{
  (event: 'toggle', status: StageResultStatus | null): void
}>()

/** 카드로 노출할 상태. 철회는 건수가 드물어 필터 셀렉트로만 고른다. */
const CARD_STATUSES: { status: StageResultStatus; label: string; tone: string }[] = [
  { status: 'PENDING', label: '대기', tone: 'pending' },
  { status: 'PASSED', label: '합격', tone: 'passed' },
  { status: 'FAILED', label: '불합격', tone: 'failed' },
  { status: 'HOLD', label: '보류', tone: 'hold' },
  { status: 'ABSENT', label: '결시', tone: 'absent' },
]

const countOf = (status: StageResultStatus) =>
  props.results.filter((result) => result.resultStatus === status).length
</script>

<template>
  <div class="counts">
    <button
      type="button"
      class="count-card"
      :class="{ active: activeStatus === null }"
      :aria-pressed="activeStatus === null"
      :aria-label="`전체 결과 보기, ${results.length}건`"
      @click="emit('toggle', null)"
    >
      <span class="count-label">대상 전체</span>
      <span class="count-value">{{ results.length }}</span>
    </button>
    <button
      v-for="card in CARD_STATUSES"
      :key="card.status"
      type="button"
      class="count-card"
      :class="[card.tone, { active: activeStatus === card.status }]"
      :aria-pressed="activeStatus === card.status"
      :aria-label="`${card.label} 결과만 보기, ${countOf(card.status)}건`"
      @click="emit('toggle', activeStatus === card.status ? null : card.status)"
    >
      <span class="count-label">{{ card.label }}</span>
      <span class="count-value">{{ countOf(card.status) }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.counts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.count-card {
  flex: 1 1 88px;
  min-width: 88px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: var(--app-border-strong);
  }

  &.active {
    border-color: var(--app-color-primary);
    background: var(--app-bg-selected);
  }
}

.count-label {
  font-size: 11px;
  color: var(--app-text-muted);
}

.count-value {
  font-size: 18px;
  font-weight: 800;
  line-height: 1.2;
}

.pending .count-value {
  color: var(--app-color-warning);
}

.passed .count-value {
  color: var(--app-color-success);
}

.failed .count-value {
  color: var(--app-color-error);
}

.hold .count-value {
  color: var(--app-color-info);
}
</style>
