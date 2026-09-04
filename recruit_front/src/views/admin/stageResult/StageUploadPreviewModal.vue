<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import type { TableColumnsType } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { saveBlobResponse } from '@/common/fileDownload'
import {
  STAGE_RESULT_STATUS_LABELS,
  type ApiFailurePayload,
  type StageResultStatus,
  type StageResultUploadCommit,
  type StageResultUploadDiff,
  type StageResultUploadPreview,
  type StageResultUploadRow,
  type StageResultUploadRowStatus,
} from '@/types/admin/stage'

const props = defineProps<{
  open: boolean
  stageId: number | null
  stageName: string
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 적용 성공. 본체가 결과를 재조회한다. */
  (event: 'applied'): void
}>()

const file = ref<File | null>(null)
const preview = ref<StageResultUploadPreview | null>(null)
/** 거부된 commit 결과. 적용 실패 시 failedRows 를 보여주기 위해 둔다. */
const rejected = ref<StageResultUploadCommit | null>(null)
/** 파일 자체가 거부된 경우(구 템플릿·확장자·크기). 행 결과가 없어 문구만 보여준다. */
const fileError = ref<string | null>(null)
const previewing = ref(false)
const committing = ref(false)
const showAllRows = ref(false)

const rowStatusMeta: Record<StageResultUploadRowStatus, { label: string; color: string }> = {
  CHANGED: { label: '변경', color: 'orange' },
  UNCHANGED: { label: '미변경', color: 'default' },
  ERROR: { label: '오류', color: 'red' },
  STALE: { label: '충돌', color: 'red' },
}

const columns: TableColumnsType = [
  // 행 번호만 dataIndex 로 그린다. 나머지는 아래 bodyCell 슬롯에서 가공해 그린다.
  { title: '행', key: 'rowNumber', dataIndex: 'rowNumber', width: 60 },
  { title: '수험번호', key: 'applicationId', width: 100 },
  { title: '이름', key: 'applicantName', width: 100 },
  { title: '결과', key: 'result', width: 160 },
  { title: '점수', key: 'score', width: 120 },
  { title: '코멘트', key: 'comment', width: 200 },
  { title: '판정', key: 'status', width: 260 },
]

/** 표시 대상 행. 거부된 commit 이 있으면 그 실패 행을, 아니면 preview 행을 본다. */
const sourceRows = computed<StageResultUploadRow[]>(
  () => rejected.value?.failedRows ?? preview.value?.rows ?? [],
)

const visibleRows = computed(() =>
  showAllRows.value
    ? sourceRows.value
    : sourceRows.value.filter((row) => row.status !== 'UNCHANGED'),
)

const committable = computed(() => preview.value?.committable === true && rejected.value === null)

/** 요약 칩의 출처. 거부된 commit 이 있으면 그쪽 카운트가 최신이다 — preview 이후 값이 바뀌었을 수 있다. */
const summary = computed(() => rejected.value ?? preview.value)

/*
 * a-table 의 bodyCell 슬롯은 record 를 Record<string, any> 로 넘겨서 StageResultUploadRow 로 바로 못 쓴다.
 * 그래서 행을 통째로 받는 대신 타입이 붙은 함수에 필요한 필드만 넘긴다(StageResultGrid 와 같은 방식).
 */
const rowStatusLabel = (status: StageResultUploadRowStatus) => rowStatusMeta[status].label

const rowStatusColor = (status: StageResultUploadRowStatus) => rowStatusMeta[status].color

/** ERROR·STALE 사유. 빈 문자열이면 템플릿에서 가린다. */
const errorText = (errors: string[]) => errors.join(' / ')

/** 값이 없거나 빈 문자열이면 '-' 로 대체한다. */
const orDash = (value: string | number | null): string => {
  if (value === null) {
    return '-'
  }
  const text = String(value)
  return text.length === 0 ? '-' : text
}

/** enum 이름으로 오는 diff 값을 한글 라벨로 바꾼다. 알 수 없는 값은 그대로 보여준다. */
const statusLabel = (raw: string | null): string => {
  if (raw === null) {
    return '-'
  }
  return STAGE_RESULT_STATUS_LABELS[raw as StageResultStatus] ?? raw
}

/*
 * diff 는 CHANGED·STALE 행에만 있다. 값이 없는 이유가 행마다 달라서 문구를 나눈다 —
 * UNCHANGED 는 "바꿀 게 없는 행"이고(전체 보기에서만 나온다), ERROR 는 파싱이 실패해 비교할 값 자체가 없다.
 * 둘을 똑같이 '-' 로 두면 미변경 행이 "데이터 없음"으로 읽힌다.
 */
const noDiffText = (status: StageResultUploadRowStatus) =>
  status === 'UNCHANGED' ? '변경 없음' : '-'

/** 결과 열이 이미 "변경 없음"을 말하므로 점수·코멘트는 비운다(같은 문구를 한 행에 세 번 쓰지 않는다). */
const noDiffValueText = (status: StageResultUploadRowStatus) => (status === 'UNCHANGED' ? '' : '-')

const resultDiffText = (diff: StageResultUploadDiff | null, status: StageResultUploadRowStatus) =>
  diff === null
    ? noDiffText(status)
    : `${statusLabel(diff.oldResultStatus)} → ${statusLabel(diff.newResultStatus)}`

const scoreDiffText = (diff: StageResultUploadDiff | null, status: StageResultUploadRowStatus) =>
  diff === null ? noDiffValueText(status) : `${orDash(diff.oldScore)} → ${orDash(diff.newScore)}`

const commentDiffText = (diff: StageResultUploadDiff | null, status: StageResultUploadRowStatus) =>
  diff === null ? noDiffValueText(status) : `${orDash(diff.oldComment)} → ${orDash(diff.newComment)}`

const reset = () => {
  file.value = null
  preview.value = null
  rejected.value = null
  fileError.value = null
  showAllRows.value = false
}

const close = () => {
  emit('update:open', false)
}

/** 모달을 열 때마다 이전 결과를 지운다. 다른 단계의 결과가 남아 보이면 안 된다. */
watch(
  () => props.open,
  (open) => {
    if (open) {
      reset()
    }
  },
)

const downloadTemplate = async () => {
  if (props.stageId === null) {
    return
  }
  try {
    const response = await adminStageApi.downloadUploadTemplate(props.stageId)
    saveBlobResponse(response, `${props.stageName}_결과등록양식.xlsx`)
  } catch (error) {
    message.error(getApiErrorMessage(error, '템플릿을 내려받지 못했습니다.'))
  }
}

/**
 * 400·409 응답 본문에서 commit 결과를 꺼낸다. 백엔드가 거부 시에도 data 에 결과를 담아 주므로
 * 행별 실패 사유를 보여줄 수 있다. 파일 자체가 거부된 경우엔 data 가 없어 null 이 나온다.
 */
const extractCommitPayload = (error: unknown): StageResultUploadCommit | null => {
  if (!axios.isAxiosError<ApiFailurePayload<StageResultUploadCommit>>(error)) {
    return null
  }
  return error.response?.data?.data ?? null
}

const selectFile = async (selected: File) => {
  if (props.stageId === null) {
    return
  }
  reset()
  file.value = selected
  previewing.value = true
  try {
    const response = await adminStageApi.previewUpload(props.stageId, selected)
    preview.value = response.data.data
  } catch (error) {
    // 파일 레벨 거부(구 영문 템플릿·확장자·크기)는 행 결과가 없다. 문구만 보여준다.
    fileError.value = getApiErrorMessage(error, '업로드 파일을 읽지 못했습니다.')
  } finally {
    previewing.value = false
  }
}

/** a-upload 가 자동 전송하지 않게 false 를 돌려준다. 전송은 우리가 직접 한다. */
const beforeUpload = (selected: File) => {
  void selectFile(selected)
  return false
}

const commit = async () => {
  if (props.stageId === null || file.value === null) {
    return
  }
  // 이전 시도에서 남은 문구를 지운다. 남으면 새 거부 배너와 옛 문구가 함께 보인다.
  fileError.value = null
  committing.value = true
  try {
    const response = await adminStageApi.commitUpload(props.stageId, file.value)
    const applied = response.data.data
    message.success(`${applied.changedCount}건을 반영했습니다.`)
    emit('applied')
    close()
  } catch (error) {
    const payload = extractCommitPayload(error)
    if (payload === null) {
      fileError.value = getApiErrorMessage(error, '업로드를 적용하지 못했습니다.')
    } else {
      rejected.value = payload
      showAllRows.value = false
    }
  } finally {
    committing.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="`엑셀 업로드 · ${stageName}`"
    width="1000px"
    :mask-closable="false"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <div class="upload-bar">
      <a-upload :before-upload="beforeUpload" :show-upload-list="false" accept=".xlsx">
        <a-button :loading="previewing">{{ file ? '파일 변경' : '파일 선택' }}</a-button>
      </a-upload>
      <span v-if="file" class="file-name">{{ file.name }}</span>
      <span class="bar-spacer" />
      <a-button @click="downloadTemplate">엑셀 템플릿 내려받기</a-button>
    </div>

    <a-alert
      v-if="!file && !fileError"
      class="hint"
      type="info"
      show-icon
      message="템플릿 다운로드 파일만 업로드할 수 있습니다."
      description="결과 목록 다운로드 파일은 열 구성이 달라 업로드할 수 없습니다. 결과·점수·코멘트만 고치고 나머지 열은 그대로 두세요."
    />

    <a-alert v-if="fileError" class="hint" type="error" show-icon :message="fileError" />

    <template v-if="preview">
      <div v-if="summary" class="summary">
        <a-tag>총 {{ summary.totalRows }}행</a-tag>
        <a-tag color="orange">변경 {{ summary.changedCount }}</a-tag>
        <a-tag>미변경 {{ summary.unchangedCount }}</a-tag>
        <a-tag :color="summary.errorCount > 0 ? 'red' : 'default'">
          오류 {{ summary.errorCount }}
        </a-tag>
        <!-- 충돌은 commit 결과에만 있는 값이라 rejected 로 판정한다(그때 summary 는 rejected 와 같다). -->
        <a-tag v-if="rejected && rejected.staleCount > 0" color="red">
          충돌 {{ rejected.staleCount }}
        </a-tag>
        <span class="bar-spacer" />
        <!-- 거부 후에는 failedRows 만 보여 미변경 행이 없다. 켜도 반응이 없어 보이지 않게 잠근다. -->
        <a-switch v-model:checked="showAllRows" size="small" :disabled="rejected !== null" />
        <span class="switch-label">{{ rejected ? '실패 행만 표시 중' : '미변경 행도 보기' }}</span>
      </div>

      <a-alert
        v-if="rejected"
        class="hint"
        type="error"
        show-icon
        :message="
          rejected.outcome === 'REJECTED_STALE'
            ? '다른 관리자가 먼저 값을 바꿔 적용하지 않았습니다.'
            : '오류가 있어 적용하지 않았습니다.'
        "
        :description="
          rejected.outcome === 'REJECTED_STALE'
            ? '템플릿을 다시 내려받아 최신 값으로 편집한 뒤 올려주세요. 한 행이라도 충돌하면 전체가 반영되지 않습니다.'
            : '아래 행을 고친 뒤 다시 올려주세요. 한 행이라도 오류가 있으면 전체가 반영되지 않습니다.'
        "
      />
      <a-alert
        v-else-if="preview.errorCount > 0"
        class="hint"
        type="error"
        show-icon
        :message="`오류 ${preview.errorCount}건이 있어 적용할 수 없습니다.`"
        description="한 행이라도 오류가 있으면 전체가 반영되지 않습니다. 파일을 고친 뒤 다시 올려주세요."
      />

      <a-table
        :columns="columns"
        :data-source="visibleRows"
        :pagination="{ pageSize: 10 }"
        row-key="rowNumber"
        size="small"
        :scroll="{ x: 1000, y: 320 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'applicationId'">{{ orDash(record.applicationId) }}</template>
          <template v-else-if="column.key === 'applicantName'">
            {{ orDash(record.applicantName) }}
          </template>

          <template v-else-if="column.key === 'result'">
            {{ resultDiffText(record.diff, record.status) }}
          </template>

          <template v-else-if="column.key === 'score'">
            {{ scoreDiffText(record.diff, record.status) }}
          </template>

          <template v-else-if="column.key === 'comment'">
            {{ commentDiffText(record.diff, record.status) }}
          </template>

          <template v-else-if="column.key === 'status'">
            <a-tag :color="rowStatusColor(record.status)">{{ rowStatusLabel(record.status) }}</a-tag>
            <span v-if="errorText(record.errors).length > 0" class="row-error">
              {{ errorText(record.errors) }}
            </span>
          </template>
        </template>
      </a-table>
    </template>

    <template #footer>
      <span class="footer-note">
        적용하면 변경 행이 한 번에 반영됩니다. 미리보기 이후 다른 관리자가 바꾼 행이 있으면 전체가
        거부됩니다.
      </span>
      <a-button @click="close">닫기</a-button>
      <a-button type="primary" :disabled="!committable" :loading="committing" @click="commit">
        적용<template v-if="preview"> ({{ preview.changedCount }}건)</template>
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.upload-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.bar-spacer {
  flex: 1;
}

.file-name {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.hint {
  margin-bottom: 12px;
}

.summary {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.switch-label {
  font-size: 12px;
  color: var(--app-text-muted);
}

.row-error {
  margin-left: 6px;
  font-size: 11.5px;
  color: var(--app-color-error);
}

.footer-note {
  float: left;
  max-width: 60%;
  text-align: left;
  font-size: 11.5px;
  color: var(--app-text-muted);
  line-height: 1.4;
}
</style>
