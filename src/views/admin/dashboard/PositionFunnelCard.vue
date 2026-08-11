<script setup lang="ts">
import { computed, ref } from 'vue'

import DashboardCard from './DashboardCard.vue'
import TableToggleButton from './TableToggleButton.vue'
import { CHART_SERIES_COLORS } from '@/common/chartPalette'
import type { DimensionFunnel } from '@/types/statistics'

/*
 * 분야별 퍼널 비교는 스몰 멀티플이다. 분야를 한 차트에 여러 시리즈로 올리면 색이 금방 8슬롯을 넘고,
 * 넘는 순간 CVD 분리가 깨진다. 축을 공유하는 작은 차트로 나누면 개수 제한 없이 비교할 수 있다.
 */
const props = defineProps<{
  groups: DimensionFunnel[]
}>()

const showTable = ref(false)

/* 모든 facet이 같은 색을 쓴다. 분야는 명목 카테고리라 값 램프를 쓰면 막대 길이를 색으로 이중 인코딩하게 된다. */
const BAR_COLOR = CHART_SERIES_COLORS[0]

interface FacetRow {
  label: string
  count: number
  share: number
}

interface Facet {
  key: string
  name: string
  total: number
  finalCount: number
  rows: FacetRow[]
}

const facets = computed<Facet[]>(() => {
  return props.groups.map((group, index) => {
    const total = group.population.p
    const rows: FacetRow[] = [
      { label: '지원', count: total, share: 1 },
      ...group.stages.map((stage) => ({
        label: stage.stageName,
        count: stage.funnelPassedCount,
        share: total === 0 ? 0 : stage.funnelPassedCount / total,
      })),
    ]

    const lastRow = rows[rows.length - 1]

    return {
      key: `${group.groupId ?? 'other'}-${index}`,
      name: group.groupName,
      total,
      finalCount: lastRow ? lastRow.count : total,
      rows,
    }
  })
})

/* 표 보기는 facet들이 같은 단계 축을 공유한다는 전제 위에 열을 만든다. */
const stageLabels = computed<string[]>(() => {
  const first = facets.value[0]

  return first ? first.rows.map((row) => row.label) : []
})

const formatCount = (value: number): string => value.toLocaleString('ko-KR')
</script>

<template>
  <DashboardCard title="분야별 퍼널 비교" subtitle="스몰 멀티플 · 축 공유">
    <template #action>
      <TableToggleButton v-model="showTable" />
    </template>

    <p v-if="facets.length === 0" class="empty">분야별 데이터가 없습니다.</p>

    <table v-else-if="showTable" class="data-table">
      <thead>
        <tr>
          <th scope="col">분야</th>
          <th v-for="label in stageLabels" :key="label" scope="col" class="numeric">
            {{ label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="facet in facets" :key="facet.key">
          <th scope="row">{{ facet.name }}</th>
          <td v-for="row in facet.rows" :key="row.label" class="numeric">
            {{ formatCount(row.count) }}
          </td>
        </tr>
      </tbody>
    </table>

    <div v-else class="facet-grid">
      <div v-for="facet in facets" :key="facet.key" class="facet">
        <p class="facet-name">
          {{ facet.name }}
          <span class="facet-range">
            {{ formatCount(facet.total) }} → {{ formatCount(facet.finalCount) }}
          </span>
        </p>

        <div v-for="row in facet.rows" :key="row.label" class="facet-row">
          <span class="facet-label">{{ row.label }}</span>
          <span class="facet-track">
            <span
              class="facet-bar"
              :style="{ width: `${row.share * 100}%`, background: BAR_COLOR }"
            />
          </span>
          <span class="facet-count">{{ formatCount(row.count) }}</span>
        </div>
      </div>
    </div>
  </DashboardCard>
</template>

<style scoped lang="scss">
.facet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 18px 22px;
}

.facet {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.facet-name {
  margin: 0 0 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-primary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.facet-range {
  margin-left: 5px;
  font-weight: 400;
  color: var(--app-text-muted);
  font-variant-numeric: tabular-nums;
}

.facet-row {
  display: grid;
  grid-template-columns: 30px 1fr 38px;
  align-items: center;
  gap: 7px;
}

.facet-label {
  font-size: 10px;
  color: var(--app-text-muted);
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.facet-track {
  height: 10px;
  background: var(--app-bg-page);
  display: block;
}

.facet-bar {
  display: block;
  height: 100%;
  min-width: 2px;
}

.facet-count {
  font-size: 10px;
  color: var(--app-text-primary);
  font-variant-numeric: tabular-nums;
  text-align: right;
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
