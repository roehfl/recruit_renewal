<script setup lang="ts">
import { computed, ref } from 'vue'

import DashboardCard from './DashboardCard.vue'
import TableToggleButton from './TableToggleButton.vue'
import { CHART_SERIES_COLORS } from '@/common/chartPalette'
import type { DimensionFunnel } from '@/types/statistics'

/*
 * 학교별·자격별처럼 이름이 긴 명목 카테고리의 top N 분포. 이름 길이 때문에 가로 막대를 쓴다.
 * 모든 막대가 같은 색이다 — 명목 축에 값 램프를 쓰면 막대 길이를 색으로 이중 인코딩하는 것이다.
 */
const props = defineProps<{
  title: string
  subtitle?: string
  groups: DimensionFunnel[]
}>()

const showTable = ref(false)

const BAR_COLOR = CHART_SERIES_COLORS[0]
const OTHER_GROUP_NAME = '기타'

interface GroupRow {
  key: string
  name: string
  count: number
  share: number
  isOther: boolean
}

/*
 * 막대 길이는 최댓값 대비 비율이다. 전체 P 대비로 그리면 top N이 전부 짧아져 서로 비교가 안 된다.
 * '기타'는 개별 그룹이 아니라 잔여 묶음이라 중립적인 회색으로 빼서 순위 오독을 막는다.
 */
const rows = computed<GroupRow[]>(() => {
  const counts = props.groups.map((group) => group.population.p)
  const max = counts.length > 0 ? Math.max(...counts) : 0

  return props.groups.map((group, index) => ({
    key: `${group.groupId ?? 'other'}-${index}`,
    name: group.groupName,
    count: group.population.p,
    share: max === 0 ? 0 : group.population.p / max,
    isOther: group.groupName === OTHER_GROUP_NAME,
  }))
})

const formatCount = (value: number): string => value.toLocaleString('ko-KR')
</script>

<template>
  <DashboardCard :title="title" :subtitle="subtitle">
    <template #action>
      <TableToggleButton v-model="showTable" />
    </template>

    <p v-if="rows.length === 0" class="empty">집계된 데이터가 없습니다.</p>

    <table v-else-if="showTable" class="data-table">
      <thead>
        <tr>
          <th scope="col">구분</th>
          <th scope="col" class="numeric">인원</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.key">
          <th scope="row">{{ row.name }}</th>
          <td class="numeric">{{ formatCount(row.count) }}</td>
        </tr>
      </tbody>
    </table>

    <div v-else class="group-list">
      <div v-for="row in rows" :key="row.key" class="group-row">
        <span class="group-name" :title="row.name">{{ row.name }}</span>
        <span class="group-track">
          <span
            class="group-bar"
            :style="{
              width: `${row.share * 100}%`,
              background: row.isOther ? 'var(--app-border-default)' : BAR_COLOR,
            }"
          />
        </span>
        <span class="group-count">{{ formatCount(row.count) }}</span>
      </div>
    </div>
  </DashboardCard>
</template>

<style scoped lang="scss">
.group-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.group-row {
  display: grid;
  grid-template-columns: 92px 1fr 40px;
  align-items: center;
  gap: 8px;
}

.group-name {
  font-size: 11px;
  color: var(--app-text-secondary);
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.group-track {
  height: 12px;
  background: var(--app-bg-page);
  display: block;
}

.group-bar {
  display: block;
  height: 100%;
  min-width: 2px;
}

.group-count {
  font-size: 11px;
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
