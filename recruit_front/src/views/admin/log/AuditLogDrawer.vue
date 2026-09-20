<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { adminAuditApi } from '@/api/admin/adminAuditApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { AuditActivityResponse } from '@/types/admin/auditLog'
import { actionResultTag, actionTypeLabel, actorTypeLabel, prettyJson, targetTypeLabel } from './logLabel'

/*
 * 감사 로그 1건 상세. 목록 행 데이터를 쓰지 않고 단건 API 를 다시 부른다.
 * 값은 같지만 404 로 "없는 행"을 구분할 수 있고, 목록 페이지가 바뀌어도 drawer 내용이 흔들리지 않는다.
 */

const props = defineProps<{ open: boolean; activityId: number | null }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'open-applicant-events', applicationId: number): void
}>()

const activity = ref<AuditActivityResponse | null>(null)
const loading = ref(false)

/* 닫거나 다른 행을 연 뒤 늦게 도착한 응답은 요청 번호로 버린다. */
let request = 0

const load = async (id: number): Promise<void> => {
  const current = ++request
  loading.value = true
  try {
    const response = await adminAuditApi.getActivity(id)
    if (current !== request) return
    activity.value = response.data.data
  } catch (error) {
    if (current !== request) return
    message.error(getApiErrorMessage(error, '감사 로그를 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch(
  () => [props.open, props.activityId] as const,
  ([open, id]) => {
    if (open && id !== null) {
      void load(id)
    }
    if (!open) {
      activity.value = null
    }
  },
  { immediate: true },
)

const close = (): void => emit('update:open', false)

const goApplicantEvents = (): void => {
  if (activity.value?.applicationId == null) return
  emit('open-applicant-events', activity.value.applicationId)
  close()
}
</script>

<template>
  <a-drawer
    :open="props.open"
    :width="760"
    :title="activity ? `감사 로그 #${activity.id}` : '감사 로그'"
    @close="close"
  >
    <a-spin :spinning="loading">
      <template v-if="activity">
        <a-descriptions title="행위" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="일시">{{ formatDate(activity.occurredAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="행위">
            {{ actionTypeLabel(activity.actionType) }}
            <span class="code">{{ activity.actionType }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="결과">
            <a-tag :color="actionResultTag(activity.actionResult).color">
              {{ actionResultTag(activity.actionResult).label }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="대상">
            {{ targetTypeLabel(activity.targetType) }}
            <span v-if="activity.targetId" class="code">{{ activity.targetId }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="사유 코드">{{ activity.reasonCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사유 메시지">{{ activity.reasonMessage ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="행위자" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="행위자 ID">{{ activity.actorId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="행위자 유형">{{ actorTypeLabel(activity.actorType) }}</a-descriptions-item>
          <a-descriptions-item label="행위 시점 권한">{{ activity.actorRoleSnapshot ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="IP 주소">{{ activity.ipAddress ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="User-Agent">{{ activity.userAgent ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="연결 정보" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="공고 ID">{{ activity.jobPostingId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원번호">{{ activity.applicationId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원자 가명키">{{ activity.applicantRefHash ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="추적번호">{{ activity.correlationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="section">
          <h4 class="section-title">메타데이터</h4>
          <pre v-if="activity.metadataJson" class="json">{{ prettyJson(activity.metadataJson) }}</pre>
          <p v-else class="empty-text">없음</p>
        </div>
      </template>
    </a-spin>

    <template #footer>
      <div class="footer">
        <a-button v-if="activity?.applicationId != null" @click="goApplicantEvents">
          이 지원서의 지원자 이벤트 보기
        </a-button>
        <span v-else class="empty-text">이 행위는 특정 지원서에 연결되어 있지 않습니다.</span>
        <a-button @click="close">닫기</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<style scoped lang="scss">
.section {
  margin-bottom: 18px;
}

.section-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 500;
}

.code {
  margin-left: 6px;
  font-family: monospace;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.json {
  margin: 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
}

.empty-text {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
</style>
