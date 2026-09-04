<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { adminStageApi } from '@/api/admin/adminStageApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import {
  DECIDABLE_RESULT_STATUSES,
  STAGE_RESULT_STATUS_COLORS,
  STAGE_RESULT_STATUS_LABELS,
  type AdminStageResult,
  type StageResultCorrectionHistory,
  type StageResultStatus,
} from '@/types/admin/stage'

const props = defineProps<{
  open: boolean
  stageId: number | null
  /** 마스크가 스텝퍼를 가리므로 어느 단계를 정정하는지 모달 안에서 보여준다(설계 §4.6). */
  stageName: string
  /** 정정 대상 행. 모달을 열 때 본체가 넘긴다. */
  target: AdminStageResult | null
}>()

const emit = defineEmits<{
  (event: 'update:open', open: boolean): void
  /** 정정 성공. 본체가 결과를 재조회한다. */
  (event: 'corrected'): void
}>()

const resultStatus = ref<StageResultStatus>('PASSED')
const score = ref<number | null>(null)
const comment = ref<string | null>(null)
const reason = ref('')
const submitting = ref(false)
const histories = ref<StageResultCorrectionHistory[]>([])
const loadingHistories = ref(false)
/** 이력 조회 실패. 빈 목록과 구분해야 "정정된 적 없음"으로 오판하지 않는다. */
const historiesLoadFailed = ref(false)

const statusOptions = DECIDABLE_RESULT_STATUSES.map((status) => ({
  value: status,
  label: STAGE_RESULT_STATUS_LABELS[status],
}))

/** 사유는 필수다(백엔드 @NotBlank). 버튼 활성 조건을 서버 규칙과 맞춘다. */
const submittable = computed(() => reason.value.trim().length > 0 && props.target !== null)

/** 공백만 남은 코멘트는 미입력으로 본다(StageResultGrid 의 handleCommentChange 와 같은 규칙). */
const normalizedComment = computed(() => {
  const value = comment.value
  return value === null || value.trim().length === 0 ? null : value
})

const statusLabel = (status: StageResultStatus) => STAGE_RESULT_STATUS_LABELS[status]
const statusColor = (status: StageResultStatus) => STAGE_RESULT_STATUS_COLORS[status]

/**
 * 이력 한 줄에 표시할 변화 요약. 바뀐 항목만 고른다 —
 * 점수만 바뀐 정정을 "합격 → 합격" 으로만 보여주면 무엇이 바뀌었는지 알 수 없다.
 */
const historyChangeText = (history: StageResultCorrectionHistory): string => {
  const parts: string[] = []
  if (history.previousStatus !== history.newStatus) {
    parts.push(`${statusLabel(history.previousStatus)} → ${statusLabel(history.newStatus)}`)
  }
  if (history.previousScore !== history.newScore) {
    parts.push(`점수 ${history.previousScore ?? '-'} → ${history.newScore ?? '-'}`)
  }
  if (history.previousComment !== history.newComment) {
    parts.push(`코멘트 ${history.previousComment ?? '-'} → ${history.newComment ?? '-'}`)
  }
  // 세 값이 모두 같은 정정은 이론상 없지만, 빈 줄을 남기지 않도록 상태를 보여준다.
  return parts.length > 0 ? parts.join(' · ') : statusLabel(history.newStatus)
}

const loadHistories = async () => {
  if (props.stageId === null || props.target === null) {
    histories.value = []
    return
  }
  loadingHistories.value = true
  try {
    const response = await adminStageApi.getCorrectionHistories(
      props.stageId,
      props.target.stageResultId,
    )
    histories.value = response.data.data
    historiesLoadFailed.value = false
  } catch {
    // 이력은 부가 정보라 실패해도 정정 자체를 막지 않는다. 대신 실패를 화면에 드러낸다.
    histories.value = []
    historiesLoadFailed.value = true
  } finally {
    loadingHistories.value = false
  }
}

/*
 * 모달을 열 때마다 대상 행의 현재 값으로 폼을 채우고 이력을 읽는다.
 * target 도 함께 보는 이유: 본체가 모달을 닫지 않고 대상만 갈아끼워도 폼이 이전 행 값으로 남지 않게 한다.
 * 둘을 같은 tick 에 세팅해도 pre-flush 배칭으로 콜백은 한 번만 돈다.
 */
watch(
  () => [props.open, props.target] as const,
  () => {
    if (!props.open || props.target === null) {
      return
    }
    // 현재 값이 대기면 셀렉트 기본값을 합격으로 둔다(대기는 정정 값으로 보낼 수 없다).
    resultStatus.value =
      props.target.resultStatus === 'PENDING' ? 'PASSED' : props.target.resultStatus
    score.value = props.target.score
    comment.value = props.target.comment
    reason.value = ''
    historiesLoadFailed.value = false
    void loadHistories()
  },
)

const close = () => {
  emit('update:open', false)
}

const submit = async () => {
  if (props.stageId === null || props.target === null) {
    return
  }
  submitting.value = true
  try {
    await adminStageApi.correctResult(props.stageId, props.target.stageResultId, {
      resultStatus: resultStatus.value,
      score: score.value,
      // 코멘트를 지우면 a-input 이 빈 문자열을 남긴다. 그리드와 같이 미입력은 null 로 보낸다.
      comment: normalizedComment.value,
      reason: reason.value.trim(),
    })
    message.success('결과를 정정했습니다.')
    emit('corrected')
    close()
  } catch (error) {
    message.error(getApiErrorMessage(error, '결과를 정정하지 못했습니다.'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <a-modal
    :open="open"
    :title="target ? `결과 정정 · ${target.applicationId} ${target.applicantName}` : '결과 정정'"
    width="640px"
    :mask-closable="false"
    @update:open="(next: boolean) => emit('update:open', next)"
  >
    <a-alert
      class="notice"
      type="warning"
      show-icon
      message="발표 후 정정입니다."
      description="사유가 이력에 남고, 지원자 화면에는 최신 결과만 표시됩니다."
    />

    <dl v-if="target" class="current">
      <dt>단계</dt>
      <dd>{{ stageName }}</dd>
      <dt>현재 결과</dt>
      <dd>
        <a-tag :color="statusColor(target.resultStatus)">{{ statusLabel(target.resultStatus) }}</a-tag>
        <span v-if="target.score !== null">{{ target.score }}점</span>
      </dd>
      <dt>현재 코멘트</dt>
      <dd>{{ target.comment ?? '-' }}</dd>
    </dl>

    <a-form layout="vertical">
      <a-form-item label="변경할 결과">
        <a-select v-model:value="resultStatus" :options="statusOptions" />
      </a-form-item>
      <a-form-item label="점수">
        <a-input-number v-model:value="score" style="width: 100%" />
      </a-form-item>
      <a-form-item label="코멘트">
        <a-input v-model:value="comment" :maxlength="2000" />
      </a-form-item>
      <a-form-item label="정정 사유" required>
        <a-textarea
          v-model:value="reason"
          :rows="3"
          :maxlength="1000"
          show-count
          placeholder="예: 채점 누락분 반영 (면접위원 B 점수표)"
        />
      </a-form-item>
    </a-form>

    <section v-if="histories.length > 0 || historiesLoadFailed" class="histories">
      <h4>
        정정 이력<template v-if="!historiesLoadFailed"> {{ histories.length }}건</template>
      </h4>
      <p v-if="historiesLoadFailed" class="load-failed">정정 이력을 불러오지 못했습니다.</p>
      <a-spin v-else :spinning="loadingHistories">
        <ul>
          <li v-for="history in histories" :key="history.historyId">
            <span class="when">{{ formatDate(history.correctedAt, 'YYYY-MM-DD HH:mm') }}</span>
            <span class="who">{{ history.correctedBy }}</span>
            <span class="change">{{ historyChangeText(history) }}</span>
            <span class="reason">{{ history.reason }}</span>
          </li>
        </ul>
      </a-spin>
    </section>

    <template #footer>
      <a-button @click="close">취소</a-button>
      <a-button type="primary" :disabled="!submittable" :loading="submitting" @click="submit">
        정정 저장
      </a-button>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.notice {
  margin-bottom: 12px;
}

.current {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 4px 10px;
  margin-bottom: 12px;
  font-size: 13px;

  dt {
    color: var(--app-text-secondary);
  }

  dd {
    margin: 0;
    font-weight: 600;
  }
}

.histories {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-default);

  h4 {
    margin: 0 0 8px;
    font-size: 13px;
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    // 변화 요약에 코멘트 전문이 들어갈 수 있어 한 줄을 넘치면 접히게 둔다.
    display: flex;
    flex-wrap: wrap;
    gap: 4px 8px;
    align-items: baseline;
    padding: 6px 0;
    border-bottom: 1px solid var(--app-border-subtle);
    font-size: 12px;

    &:last-child {
      border-bottom: none;
    }
  }

  .load-failed {
    margin: 0;
    color: var(--app-text-secondary);
    font-size: 12px;
  }

  .when {
    color: var(--app-text-muted);
    white-space: nowrap;
  }

  .who {
    color: var(--app-text-secondary);
    white-space: nowrap;
  }

  .change {
    font-weight: 600;
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .reason {
    color: var(--app-text-secondary);
    min-width: 0;
    overflow-wrap: anywhere;
  }
}
</style>
