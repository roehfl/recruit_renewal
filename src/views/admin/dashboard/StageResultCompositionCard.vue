<script setup lang="ts">
import { computed, ref } from 'vue'

import DashboardCard from './DashboardCard.vue'
import TableToggleButton from './TableToggleButton.vue'
import {
  CHART_NEUTRAL_COLOR,
  CHART_SERIES_COLORS,
  LOW_CONTRAST_SERIES_COLORS,
} from '@/common/chartPalette'
import type { StageDistribution, StageFunnel } from '@/types/statistics'

const props = defineProps<{
  stages: StageFunnel[]
}>()

const showTable = ref(false)

/*
 * 세그먼트 순서는 고정이다. 색은 대상 자체를 따라가므로 값이 0이 되어 사라져도 남은 세그먼트의 색이 바뀌지 않는다.
 * 미확정은 결과가 아니라 결과의 '부재'라서 카테고리 슬롯이 아닌 중립색을 쓴다
 * (슬롯 6은 녹색이라 미확정에 칠하면 합격으로 오독된다).
 */
const SEGMENTS = [
  { key: 'passed', label: '합격', color: CHART_SERIES_COLORS[0] },
  { key: 'failed', label: '탈락', color: CHART_SERIES_COLORS[1] },
  { key: 'absent', label: '불참', color: CHART_SERIES_COLORS[2] },
  { key: 'withdrawn', label: '취소', color: CHART_SERIES_COLORS[3] },
  { key: 'hold', label: '보류', color: CHART_SERIES_COLORS[4] },
  { key: 'pending', label: '미확정', color: CHART_NEUTRAL_COLOR },
] as const satisfies readonly { key: keyof StageDistribution; label: string; color: string }[]

/* 라벨이 패딩까지 들어갈 수 있는 최소 점유율. 이보다 좁으면 글자가 잘리므로 표 보기로 넘긴다. */
const LABEL_MIN_SHARE = 0.12

interface StageRow {
  stageId: number
  stageName: string
  /* 그 단계의 대상자 수 = P − noResult. 스택 막대의 분모다. */
  targetCount: number
  segments: {
    key: string
    label: string
    color: string
    count: number
    share: number
    showLabel: boolean
  }[]
}

/*
 * 백엔드 distribution의 분모는 P(공고 전체)라 7버킷 합계가 항상 P다. 화면이 읽히길 원하는 것은
 * "그 단계 대상자 중 결과 구성"이므로 noResult(아직 그 단계에 도달하지 않은 인원)를 빼고 정규화한다.
 * 정규화하지 않으면 후반 단계 막대가 전부 쪼그라든다.
 */
const rows = computed<StageRow[]>(() => {
  return props.stages.map((stage) => {
    const distribution = stage.distribution
    const targetCount =
      distribution.passed +
      distribution.failed +
      distribution.absent +
      distribution.hold +
      distribution.pending +
      distribution.withdrawn

    return {
      stageId: stage.stageId,
      stageName: stage.stageName,
      targetCount,
      segments: SEGMENTS.map((segment) => {
        const count = distribution[segment.key]
        const share = targetCount === 0 ? 0 : count / targetCount

        return {
          key: segment.key,
          label: segment.label,
          color: segment.color,
          count,
          share,
          showLabel: share >= LABEL_MIN_SHARE,
        }
      }),
    }
  })
})

const hasLowContrastSegment = computed<boolean>(() => {
  return SEGMENTS.some((segment) => LOW_CONTRAST_SERIES_COLORS.includes(segment.color))
})

const formatCount = (value: number): string => value.toLocaleString('ko-KR')
</script>

<template>
  <DashboardCard title="단계별 결과 구성" subtitle="각 단계 대상자 기준">
    <template #action>
      <TableToggleButton v-model="showTable" />
    </template>

    <p v-if="rows.length === 0" class="empty">등록된 전형 단계가 없습니다.</p>

    <template v-else>
      <ul class="legend">
        <li v-for="segment in SEGMENTS" :key="segment.key">
          <span class="legend-swatch" :style="{ background: segment.color }" aria-hidden="true" />
          {{ segment.label }}
        </li>
      </ul>

      <table v-if="showTable" class="data-table">
        <thead>
          <tr>
            <th scope="col">단계</th>
            <th scope="col" class="numeric">대상</th>
            <th v-for="segment in SEGMENTS" :key="segment.key" scope="col" class="numeric">
              {{ segment.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.stageId">
            <th scope="row">{{ row.stageName }}</th>
            <td class="numeric">{{ formatCount(row.targetCount) }}</td>
            <td v-for="segment in row.segments" :key="segment.key" class="numeric">
              {{ formatCount(segment.count) }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else class="stack-list">
        <div v-for="row in rows" :key="row.stageId" class="stack-row">
          <span class="stack-label">{{ row.stageName }}</span>

          <span v-if="row.targetCount === 0" class="stack-empty">대상자 없음</span>

          <span v-else class="stack-bar">
            <span
              v-for="segment in row.segments"
              :key="segment.key"
              class="stack-segment"
              :class="{ 'is-zero': segment.count === 0 }"
              :style="{ flexGrow: segment.share, background: segment.color }"
              :title="`${segment.label} ${formatCount(segment.count)}`"
            >
              <span v-if="segment.showLabel" class="segment-label">
                {{ segment.label }} {{ formatCount(segment.count) }}
              </span>
            </span>
          </span>
        </div>
      </div>

      <p v-if="hasLowContrastSegment && !showTable" class="contrast-note">
        불참 · 취소 · 보류는 흰 배경 대비 3:1 미만입니다. 정확한 값은 표 보기로 확인하세요.
      </p>
    </template>
  </DashboardCard>
</template>

<style scoped lang="scss">
.legend {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;

  li {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    color: var(--app-text-secondary);
  }
}

.legend-swatch {
  width: 9px;
  height: 9px;
  flex: none;
}

.stack-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stack-row {
  display: grid;
  grid-template-columns: 52px 1fr;
  align-items: center;
  gap: 12px;
}

.stack-label {
  font-size: 12.5px;
  color: var(--app-text-primary);
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/*
 * 세그먼트 비율은 flex-basis가 아니라 flex-grow로 준다.
 * basis에 퍼센트를 박으면 2px 갭이 그 위에 더해져 합이 100%를 넘고 막대가 카드 폭을 뚫는다
 * (flex-shrink가 0이면 줄지도 않는다). grow로 주면 갭이 먼저 빠지고 남은 공간을 비율대로 나눠 항상 맞는다.
 */
.stack-bar {
  height: 22px;
  display: flex;
  gap: 2px;
  overflow: hidden;
}

.stack-segment {
  flex-basis: 0;
  flex-shrink: 1;
  min-width: 3px;
  display: flex;
  align-items: center;
  padding-left: 8px;
  overflow: hidden;

  /* 0건은 자리를 차지하지 않는다. min-width가 0값까지 그려버리는 것을 막는다. */
  &.is-zero {
    display: none;
  }
}

.segment-label {
  font-size: 11.5px;
  color: #fff;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stack-empty {
  font-size: 11.5px;
  color: var(--app-text-muted);
}

.contrast-note {
  margin: 14px 0 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--app-text-muted);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;

  th,
  td {
    padding: 7px 5px;
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
