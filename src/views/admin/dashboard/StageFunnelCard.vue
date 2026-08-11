<script setup lang="ts">
import { computed, ref } from 'vue'

import DashboardCard from './DashboardCard.vue'
import TableToggleButton from './TableToggleButton.vue'
import { ordinalColorAt } from '@/common/chartPalette'
import type { FunnelPopulation, StageFunnel } from '@/types/statistics'

/*
 * 단계 퍼널. 단계는 순서가 있는 축이므로 명목 카테고리 색이 아니라 순서형 램프를 쓴다.
 * 사다리꼴 도형 대신 가로 막대로 그린다 — 도형 면적이 값에 비례하지 않으면 오독을 만든다.
 */
const props = defineProps<{
  population: FunnelPopulation
  stages: StageFunnel[]
}>()

const showTable = ref(false)

interface FunnelRow {
  key: string
  label: string
  count: number
  share: number
  color: string
  /* 직전 행에서 이 행으로 넘어올 때의 전환율·이탈 수. 첫 행(지원)은 null이다. */
  stepConversionRate: number | null
  dropCount: number | null
  averageDwellDays: number | null
}

/*
 * 첫 행은 stage가 아니라 모집단 P(지원) 자체다. 이후 각 행은 그 단계의 순차 통과 인원이다.
 * 막대 길이는 P 대비 비율(cumulativeRate)이라 행끼리 같은 축에서 비교된다.
 */
const rows = computed<FunnelRow[]>(() => {
  const total = props.population.p
  const stageCount = props.stages.length

  const head: FunnelRow = {
    key: 'population',
    label: '지원',
    count: total,
    share: 1,
    color: ordinalColorAt(0, stageCount + 1),
    stepConversionRate: null,
    dropCount: null,
    averageDwellDays: null,
  }

  let previousCount = total

  const stageRows = props.stages.map((stage, index) => {
    const row: FunnelRow = {
      key: `stage-${stage.stageId}`,
      label: stage.stageName,
      count: stage.funnelPassedCount,
      share: total === 0 ? 0 : stage.funnelPassedCount / total,
      color: ordinalColorAt(index + 1, stageCount + 1),
      stepConversionRate: stage.stepConversionRate,
      dropCount: Math.max(previousCount - stage.funnelPassedCount, 0),
      averageDwellDays: stage.averageDwellDays,
    }
    previousCount = stage.funnelPassedCount
    return row
  })

  return [head, ...stageRows]
})

const overallPassRate = computed<number | null>(() => {
  const lastStage = props.stages[props.stages.length - 1]

  return lastStage ? lastStage.cumulativeRate : null
})

/* 최대 이탈 구간 = 전환율이 가장 낮은 단계. 직전 행 라벨과 함께 "A → B"로 보여준다. */
const worstStepLabel = computed<string | null>(() => {
  const stages = props.stages
  let worstIndex = 0

  for (let index = 1; index < stages.length; index += 1) {
    const candidate = stages[index]
    const current = stages[worstIndex]

    if (candidate && current && candidate.stepConversionRate < current.stepConversionRate) {
      worstIndex = index
    }
  }

  const worst = stages[worstIndex]

  if (!worst) {
    return null
  }

  const previous = stages[worstIndex - 1]

  return `${previous ? previous.stageName : '지원'} → ${worst.stageName}`
})

/* 표본이 있는 단계만 평균한다. 전 단계가 미확정이면 null이며 0일로 표시하지 않는다. */
const averageDwellDays = computed<number | null>(() => {
  const samples = props.stages
    .map((stage) => stage.averageDwellDays)
    .filter((value): value is number => value !== null)

  if (samples.length === 0) {
    return null
  }

  const sum = samples.reduce((total, value) => total + value, 0)

  return Math.round((sum / samples.length) * 10) / 10
})

const formatPercent = (value: number | null): string => {
  return value === null ? '—' : `${(value * 100).toFixed(1)}%`
}

const formatDays = (value: number | null): string => {
  return value === null ? '—' : `${value.toFixed(1)}일`
}

const formatCount = (value: number): string => value.toLocaleString('ko-KR')
</script>

<template>
  <DashboardCard title="단계 퍼널" subtitle="지원 → 각 전형 단계 통과 인원">
    <template #action>
      <TableToggleButton v-model="showTable" />
    </template>

    <p v-if="stages.length === 0" class="empty">등록된 전형 단계가 없습니다.</p>

    <table v-else-if="showTable" class="data-table">
      <thead>
        <tr>
          <th scope="col">단계</th>
          <th scope="col" class="numeric">인원</th>
          <th scope="col" class="numeric">누적 통과율</th>
          <th scope="col" class="numeric">전환율</th>
          <th scope="col" class="numeric">이탈</th>
          <th scope="col" class="numeric">평균 체류</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.key">
          <th scope="row">{{ row.label }}</th>
          <td class="numeric">{{ formatCount(row.count) }}</td>
          <td class="numeric">{{ formatPercent(row.share) }}</td>
          <td class="numeric">{{ formatPercent(row.stepConversionRate) }}</td>
          <td class="numeric">{{ row.dropCount === null ? '—' : formatCount(row.dropCount) }}</td>
          <td class="numeric">{{ formatDays(row.averageDwellDays) }}</td>
        </tr>
      </tbody>
    </table>

    <div v-else class="funnel">
      <template v-for="(row, index) in rows" :key="row.key">
        <p v-if="index > 0" class="funnel-step">
          <span aria-hidden="true">↓</span>
          전환 {{ formatPercent(row.stepConversionRate) }} · 이탈
          {{ formatCount(row.dropCount ?? 0) }}
        </p>

        <div class="funnel-row">
          <span class="funnel-label">{{ row.label }}</span>
          <span class="funnel-track">
            <span
              class="funnel-bar"
              :style="{ width: `${row.share * 100}%`, background: row.color }"
            />
          </span>
          <span class="funnel-count">{{ formatCount(row.count) }}</span>
        </div>
      </template>
    </div>

    <dl class="summary">
      <div class="summary-item">
        <dt>전체 통과율</dt>
        <dd>{{ formatPercent(overallPassRate) }}</dd>
      </div>
      <div class="summary-item">
        <dt>최대 이탈 구간</dt>
        <dd>{{ worstStepLabel ?? '—' }}</dd>
      </div>
      <div class="summary-item">
        <dt>평균 체류</dt>
        <dd>{{ formatDays(averageDwellDays) }}</dd>
      </div>
    </dl>
  </DashboardCard>
</template>

<style scoped lang="scss">
.funnel {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.funnel-row {
  display: grid;
  grid-template-columns: 52px 1fr 68px;
  align-items: center;
  gap: 12px;
}

.funnel-label {
  font-size: 12.5px;
  color: var(--app-text-primary);
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.funnel-track {
  height: 22px;
  background: var(--app-bg-page);
  display: block;
}

.funnel-bar {
  display: block;
  height: 100%;
  /* 값이 0이어도 축이 있다는 것은 보이게 한다. */
  min-width: 2px;
}

.funnel-count {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.funnel-step {
  margin: 0;
  padding-left: 64px;
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 11px;
  color: var(--app-text-secondary);
  font-variant-numeric: tabular-nums;
}

.summary {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-default);
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.summary-item {
  dt {
    font-size: 11px;
    color: var(--app-text-secondary);
  }

  dd {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
    color: var(--app-text-primary);
  }
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  font-variant-numeric: tabular-nums;

  th,
  td {
    padding: 7px 6px;
    border-bottom: 1px solid var(--app-border-default);
    text-align: left;
    font-weight: 400;
    color: var(--app-text-primary);
  }

  thead th {
    color: var(--app-text-muted);
    font-weight: 600;
  }

  .numeric {
    text-align: right;
  }
}

.empty {
  margin: 0;
  padding: 32px 0;
  font-size: 12.5px;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
