<script setup lang="ts">
import { computed, ref } from 'vue'

import DashboardCard from './DashboardCard.vue'
import TableToggleButton from './TableToggleButton.vue'
import { CHART_SERIES_COLORS } from '@/common/chartPalette'
import type { ApplicationDaily } from '@/types/statistics'

/*
 * 일자별 지원 접수 추이. 단일 시리즈라 범례를 두지 않는다 — 카드 제목이 시리즈 이름을 맡는다.
 * 라이브러리 없이 인라인 SVG로 그린다. 막대 계열은 div가 더 다루기 쉽지만 선은 좌표 계산이 필요하다.
 */
const props = defineProps<{
  daily: ApplicationDaily
}>()

const showTable = ref(false)

const VIEW_WIDTH = 600
const VIEW_HEIGHT = 120
const PADDING_TOP = 8
const PADDING_BOTTOM = 8

const maxCount = computed<number>(() => {
  const counts = props.daily.days.map((day) => day.submittedCount)

  return counts.length > 0 ? Math.max(...counts, 1) : 1
})

const points = computed<string>(() => {
  const days = props.daily.days

  if (days.length === 0) {
    return ''
  }

  const plotHeight = VIEW_HEIGHT - PADDING_TOP - PADDING_BOTTOM
  const stepX = days.length === 1 ? 0 : VIEW_WIDTH / (days.length - 1)

  return days
    .map((day, index) => {
      const x = days.length === 1 ? VIEW_WIDTH / 2 : index * stepX
      const y = PADDING_TOP + plotHeight * (1 - day.submittedCount / maxCount.value)

      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

/* 축 라벨은 처음·중간·마지막만 찍는다. 모든 날짜에 라벨을 달면 겹쳐서 읽을 수 없다. */
const axisLabels = computed<string[]>(() => {
  const days = props.daily.days

  if (days.length === 0) {
    return []
  }

  if (days.length <= 2) {
    return days.map((day) => day.date)
  }

  const first = days[0]
  const middle = days[Math.floor(days.length / 2)]
  const last = days[days.length - 1]

  if (!first || !middle || !last) {
    return []
  }

  return [first.date, middle.date, last.date]
})

const peakDay = computed(() => {
  return props.daily.days.reduce<(typeof props.daily.days)[number] | null>((best, day) => {
    return best === null || day.submittedCount > best.submittedCount ? day : best
  }, null)
})

/* 표 보기는 제출이 0건인 날을 빼서 읽기 쉽게 한다. 차트는 축이 왜곡되지 않도록 0인 날도 그대로 그린다. */
const tableRows = computed(() => props.daily.days.filter((day) => day.submittedCount > 0))

const formatCount = (value: number): string => value.toLocaleString('ko-KR')
</script>

<template>
  <DashboardCard
    title="일자별 지원 접수 추이"
    :subtitle="`${daily.from} ~ ${daily.to} · 총 ${formatCount(daily.totalSubmitted)}건`"
  >
    <template #action>
      <TableToggleButton v-model="showTable" />
    </template>

    <p v-if="daily.days.length === 0" class="empty">접수 구간 데이터가 없습니다.</p>

    <table v-else-if="showTable" class="data-table">
      <thead>
        <tr>
          <th scope="col">날짜</th>
          <th scope="col" class="numeric">제출</th>
          <th scope="col" class="numeric">누적</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="day in tableRows" :key="day.date">
          <th scope="row">{{ day.date }}</th>
          <td class="numeric">{{ formatCount(day.submittedCount) }}</td>
          <td class="numeric">{{ formatCount(day.cumulativeCount) }}</td>
        </tr>
        <tr v-if="tableRows.length === 0">
          <td colspan="3" class="table-empty">제출된 지원서가 없습니다.</td>
        </tr>
      </tbody>
    </table>

    <template v-else>
      <!--
        preserveAspectRatio="none"로 가로를 늘이면 stroke도 함께 늘어나 세로 획이 가로 획보다 두꺼워진다.
        vector-effect="non-scaling-stroke"가 선 굵기를 화면 기준으로 고정해 그 왜곡을 막는다.
      -->
      <svg
        class="trend-chart"
        :viewBox="`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`"
        preserveAspectRatio="none"
        role="img"
        :aria-label="`${daily.from}부터 ${daily.to}까지 일자별 지원 접수 추이. 총 ${daily.totalSubmitted}건.`"
      >
        <polyline
          :points="points"
          fill="none"
          :stroke="CHART_SERIES_COLORS[0]"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-linecap="round"
          vector-effect="non-scaling-stroke"
        />
      </svg>

      <div class="axis">
        <span v-for="label in axisLabels" :key="label">{{ label }}</span>
      </div>

      <p v-if="peakDay && peakDay.submittedCount > 0" class="peak-note">
        최다 접수일 {{ peakDay.date }} · {{ formatCount(peakDay.submittedCount) }}건
      </p>
    </template>
  </DashboardCard>
</template>

<style scoped lang="scss">
.trend-chart {
  width: 100%;
  height: 120px;
  display: block;
}

.axis {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--app-text-muted);
  font-variant-numeric: tabular-nums;
}

.peak-note {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--app-text-secondary);
  font-variant-numeric: tabular-nums;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;

  th,
  td {
    padding: 6px 5px;
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

.table-empty {
  color: var(--app-text-muted);
  text-align: center;
}

.empty {
  margin: 0;
  padding: 32px 0;
  font-size: 12.5px;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
