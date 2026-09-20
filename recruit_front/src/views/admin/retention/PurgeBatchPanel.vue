<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'

import { retentionApi } from '@/api/admin/retentionApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type {
  PurgeBatchDetail,
  PurgeBatchItem,
  PurgeBatchMode,
  PurgeBatchStatus,
  PurgeBatchSummary,
  PurgeItemStatus,
} from '@/types/admin/retention'
import { triggerLabel } from './retentionLabel'

/*
 * "파기 이력" 탭. 자동 파기(스케줄)와 강제 파기(삭제 요청)가 남긴 PurgeBatch 기록을 조회만 한다.
 * 이 탭의 API(getPurgeBatches·getPurgeBatch)는 RECRUIT·PRIVACY 공용이라 권한 분기가 없다.
 * 부모(AdminRetentionView)의 자동 파기 카드가 마지막 실행 batch로 바로 이동할 수 있도록
 * openBatch를 노출한다.
 */

const PAGE_SIZE = 20

const MODE_LABEL: Record<PurgeBatchMode, string> = {
  DRY_RUN: '산정',
  EXECUTE: '실행',
}

interface StatusMeta {
  label: string
  color: string
}

const BATCH_STATUS_META: Record<PurgeBatchStatus, StatusMeta> = {
  COMPLETED: { label: '완료', color: 'green' },
  PARTIAL_FAILED: { label: '일부 실패', color: 'orange' },
  FAILED: { label: '실패', color: 'red' },
  RUNNING: { label: '진행 중', color: 'blue' },
}

const ITEM_STATUS_LABEL: Record<PurgeItemStatus, string> = {
  ELIGIBLE: '파기 대상',
  SKIPPED: '스킵',
  PENDING: '보류',
  PURGED: '파기 완료',
  FAILED: '실패',
}

const ITEM_STATUS_COLOR: Record<PurgeItemStatus, string> = {
  ELIGIBLE: 'blue',
  SKIPPED: 'default',
  PENDING: 'orange',
  PURGED: 'green',
  FAILED: 'red',
}

/** 스킵 사유 한글 라벨. 목록에 없는 값은 코드 원문을 그대로 보여준다. */
const SKIP_REASON_LABEL: Record<string, string> = {
  RETENTION_NOT_DUE: '보존기간 미도래',
  RETENTION_HOLD: '파기 보류',
  ALREADY_PURGED: '이미 파기됨',
  ANCHOR_NOT_FIXED: '기산점 미확정',
  APPLICATION_NOT_TERMINAL: '전형 미종료',
  INVALID_STAGE_CONFIGURATION: '전형 설정 오류',
  POLICY_NOT_FOUND: '보존 정책 없음',
  POLICY_CONFLICT: '보존 정책 충돌',
  BINARY_DELETE_FAILED: '첨부 삭제 실패',
  PURGE_ITEM_FAILED: '처리 실패',
}

const reasonLabel = (reasonCode: string): string => SKIP_REASON_LABEL[reasonCode] ?? reasonCode

const formatCount = (value: number): string => value.toLocaleString('ko-KR')

/** requestedBy가 SYSTEM이면 스케줄러가 남긴 자동 실행 기록이다. */
const requestedByText = (requestedBy: string): string => (requestedBy === 'SYSTEM' ? '자동 실행' : requestedBy)

// ── 목록 ────────────────────────────────────────────────────────────────

const columns = [
  { title: '실행일시', key: 'startedAt', width: 150 },
  { title: '모드', key: 'mode', width: 90 },
  { title: '트리거', key: 'trigger', width: 110 },
  { title: '요청자', key: 'requestedBy', width: 120 },
  { title: '상태', key: 'status', width: 100 },
  { title: '대상/파기/스킵/실패', key: 'counts', width: 200 },
]

const rows = ref<PurgeBatchSummary[]>([])
const page = ref(0)
const totalElements = ref(0)
const loading = ref(false)

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize: PAGE_SIZE,
  total: totalElements.value,
  showSizeChanger: false,
}))

const loadList = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await retentionApi.getPurgeBatches(page.value, PAGE_SIZE)
    rows.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '파기 이력을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const handleTableChange = (nextPagination: { current?: number }): void => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadList()
}

const asRow = (record: unknown): PurgeBatchSummary => record as PurgeBatchSummary

const onRow = (record: unknown) => ({
  onClick: () => openBatch(asRow(record).id),
})

onMounted(loadList)

// ── 상세 드로어 ──────────────────────────────────────────────────────────

/*
 * 사용자가 행을 빠르게 바꿔 클릭하면 이전 요청의 응답이 나중에 도착할 수 있는데, 그걸 그대로 보여주면
 * 엉뚱한 배치의 상세가 화면에 남는다. DataSubjectDrawer와 같은 방식으로 요청마다 증가하는 번호
 * (detailRequest)를 두고, 응답이 왔을 때 그 번호가 최신 요청과 다르면(=늦게 도착한 응답이면) 버린다.
 */
const drawerOpen = ref(false)
const detail = ref<PurgeBatchDetail | null>(null)
const detailLoading = ref(false)

let detailRequest = 0

const loadDetail = async (batchId: number): Promise<void> => {
  const current = ++detailRequest
  detailLoading.value = true
  try {
    const response = await retentionApi.getPurgeBatch(batchId)
    if (current !== detailRequest) return // 늦게 도착한 응답: 그 사이 다른 배치를 열었다.
    detail.value = response.data.data
  } catch (error) {
    if (current !== detailRequest) return
    message.error(getApiErrorMessage(error, '파기 배치 상세를 불러오지 못했습니다.'))
  } finally {
    if (current === detailRequest) {
      detailLoading.value = false
    }
  }
}

const openBatch = (batchId: number): void => {
  detailRequest += 1 // 이전 요청은 이 시점에서 전부 무효화된다.
  detail.value = null
  drawerOpen.value = true
  void loadDetail(batchId)
}

defineExpose({ openBatch })

/** 스킵 사유별 집계. reasonCode가 null인 항목(적격 건)은 뺀다. */
interface SkipReasonRow {
  reasonCode: string
  label: string
  count: number
}

const skipReasonRows = computed<SkipReasonRow[]>(() => {
  const counts = new Map<string, number>()
  for (const item of detail.value?.items ?? []) {
    if (item.reasonCode === null) continue
    counts.set(item.reasonCode, (counts.get(item.reasonCode) ?? 0) + 1)
  }
  return Array.from(counts.entries())
    .map(([reasonCode, count]) => ({ reasonCode, label: reasonLabel(reasonCode), count }))
    .sort((a, b) => b.count - a.count)
})

const skipReasonColumns = [
  { title: '사유', dataIndex: 'label', key: 'label' },
  { title: '건수', dataIndex: 'count', key: 'count', width: 100 },
]

/*
 * 지원서 id 목록. dry-run은 전 지원서를 훑어 item이 수천 건일 수 있어 a-collapse에 접어 두고
 * (destroy-inactive-panel로 펼치기 전까지는 렌더링도 하지 않는다), 펼친 뒤에도 a-table의
 * 클라이언트 페이지네이션(50건씩)으로 한 번에 그리는 행 수를 제한한다.
 */
const itemColumns = [
  { title: '지원서 id', dataIndex: 'applicationId', key: 'applicationId', width: 120 },
  { title: '상태', key: 'status', width: 110 },
  { title: '사유', key: 'reason' },
]

const asItem = (record: unknown): PurgeBatchItem => record as PurgeBatchItem

const itemReasonText = (item: PurgeBatchItem): string => (item.reasonCode !== null ? reasonLabel(item.reasonCode) : '-')
</script>

<template>
  <div class="purge-batch-panel">
    <a-table
      class="batch-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :custom-row="onRow"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'startedAt'">
          {{ formatDate(asRow(record).startedAt, 'YYYY-MM-DD HH:mm') || '-' }}
        </template>
        <template v-else-if="column.key === 'mode'">{{ MODE_LABEL[asRow(record).mode] }}</template>
        <template v-else-if="column.key === 'trigger'">{{ triggerLabel(asRow(record).triggerType) }}</template>
        <template v-else-if="column.key === 'requestedBy'">{{ requestedByText(asRow(record).requestedBy) }}</template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="BATCH_STATUS_META[asRow(record).status].color">
            {{ BATCH_STATUS_META[asRow(record).status].label }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'counts'">
          {{ formatCount(asRow(record).totalCount) }} / {{ formatCount(asRow(record).purgedCount) }} /
          {{ formatCount(asRow(record).skippedCount) }} / {{ formatCount(asRow(record).failedCount) }}
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="drawerOpen" title="파기 배치 상세" width="720">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions bordered size="small" :column="2" class="batch-info">
            <a-descriptions-item label="상태">
              <a-tag :color="BATCH_STATUS_META[detail.batch.status].color">
                {{ BATCH_STATUS_META[detail.batch.status].label }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="모드">{{ MODE_LABEL[detail.batch.mode] }}</a-descriptions-item>
            <a-descriptions-item label="트리거">{{ triggerLabel(detail.batch.triggerType) }}</a-descriptions-item>
            <a-descriptions-item label="요청자">{{ requestedByText(detail.batch.requestedBy) }}</a-descriptions-item>
            <a-descriptions-item label="실행일시">
              {{ formatDate(detail.batch.startedAt, 'YYYY-MM-DD HH:mm') || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="완료일시">
              {{ formatDate(detail.batch.completedAt, 'YYYY-MM-DD HH:mm') || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="근거 dry-run 배치 id">
              {{ detail.batch.sourceDryRunBatchId ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="대상">{{ formatCount(detail.batch.totalCount) }}건</a-descriptions-item>
            <a-descriptions-item label="파기">{{ formatCount(detail.batch.purgedCount) }}건</a-descriptions-item>
            <a-descriptions-item label="보류중">{{ formatCount(detail.batch.pendingCount) }}건</a-descriptions-item>
            <a-descriptions-item label="스킵">{{ formatCount(detail.batch.skippedCount) }}건</a-descriptions-item>
            <a-descriptions-item label="실패">{{ formatCount(detail.batch.failedCount) }}건</a-descriptions-item>
          </a-descriptions>

          <h3 class="section-title">
            사유별 집계
            <span class="section-hint">스킵·실패 사유를 함께 집계합니다.</span>
          </h3>
          <a-table
            v-if="skipReasonRows.length > 0"
            :columns="skipReasonColumns"
            :data-source="skipReasonRows"
            row-key="reasonCode"
            size="small"
            :pagination="false"
          />
          <p v-else class="muted">스킵·실패한 지원서가 없습니다.</p>

          <a-collapse class="item-collapse" :bordered="false" destroy-inactive-panel>
            <a-collapse-panel key="items" :header="`지원서 id 목록 (${formatCount(detail.items.length)}건)`">
              <a-table
                :columns="itemColumns"
                :data-source="detail.items"
                row-key="id"
                size="small"
                :pagination="{ pageSize: 50, showSizeChanger: false }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'status'">
                    <a-tag :color="ITEM_STATUS_COLOR[asItem(record).status]">
                      {{ ITEM_STATUS_LABEL[asItem(record).status] }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'reason'">{{ itemReasonText(asItem(record)) }}</template>
                </template>
              </a-table>
            </a-collapse-panel>
          </a-collapse>
        </template>
        <a-empty v-else-if="!detailLoading" description="파기 배치 상세를 불러오지 못했습니다." />
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped lang="scss">
.batch-table {
  :deep(.ant-table-tbody > tr) {
    cursor: pointer;
  }
}

.batch-info {
  margin-bottom: 16px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.section-hint {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-secondary);
}

.muted {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.item-collapse {
  margin-top: 16px;
}
</style>
