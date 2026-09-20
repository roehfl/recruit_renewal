<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

import { adminClientEventApi } from '@/api/admin/adminClientEventApi'
import { getApiErrorMessage } from '@/api/apiError'
import { formatDate } from '@/common/dateUtil'
import type { ClientEventLogResponse } from '@/types/admin/clientEventLog'
import { eventTypeLabel, prettyJson, severityColor } from './logLabel'

/*
 * 지원자 이벤트 1건 상세. 목록 행 데이터를 쓰지 않고 단건 API 를 다시 부른다(404 로 없는 행을 구분한다).
 * "같은 세션 이벤트 모두 보기"는 조회 대상(지원번호)을 풀고 세션으로만 거른다 —
 * 한 세션에는 지원서 필드가 빈 행(전역 JS 오류 등)도 섞여 있어 지원번호를 유지하면 그 행들이 보이지 않는다.
 */

const props = defineProps<{ open: boolean; eventId: number | null }>()
const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'filter-session', clientSessionId: string): void
  (e: 'open-audit', applicationId: number): void
}>()

const event = ref<ClientEventLogResponse | null>(null)
const loading = ref(false)

/* 닫거나 다른 행을 연 뒤 늦게 도착한 응답은 요청 번호로 버린다. */
let request = 0

const load = async (id: number): Promise<void> => {
  const current = ++request
  loading.value = true
  try {
    const response = await adminClientEventApi.getClientEvent(id)
    if (current !== request) return
    event.value = response.data.data
  } catch (error) {
    if (current !== request) return
    message.error(getApiErrorMessage(error, '이벤트를 불러오지 못했습니다.'))
    emit('update:open', false)
  } finally {
    if (current === request) {
      loading.value = false
    }
  }
}

watch(
  () => [props.open, props.eventId] as const,
  ([open, id]) => {
    if (open && id !== null) {
      void load(id)
    }
    if (!open) {
      event.value = null
    }
  },
  { immediate: true },
)

const close = (): void => emit('update:open', false)

const goSession = (): void => {
  if (!event.value) return
  emit('filter-session', event.value.clientSessionId)
  close()
}

const goAudit = (): void => {
  if (event.value?.applicationId == null) return
  emit('open-audit', event.value.applicationId)
  close()
}
</script>

<template>
  <a-drawer
    :open="props.open"
    :width="760"
    :title="event ? `지원자 이벤트 #${event.id}` : '지원자 이벤트'"
    @close="close"
  >
    <a-spin :spinning="loading">
      <template v-if="event">
        <a-descriptions title="이벤트" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="수신시각">{{ formatDate(event.receivedAt, 'YYYY-MM-DD HH:mm:ss') }}</a-descriptions-item>
          <a-descriptions-item label="브라우저 발생시각">{{ event.clientOccurredAt ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="이벤트">
            {{ eventTypeLabel(event.eventType) }}
            <span class="code">{{ event.eventType }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="심각도">
            <a-tag :color="severityColor(event.severity)">{{ event.severity }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="메시지 코드">{{ event.message ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="수집 출처">{{ event.source }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="발생 위치" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="화면 코드">{{ event.pageCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="컴포넌트">{{ event.componentCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="라우트">{{ event.routePath ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="동작">{{ event.operation ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="공고 ID">{{ event.jobPostingId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="지원번호">{{ event.applicationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-descriptions title="HTTP" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="메서드 / 경로">
            <template v-if="event.apiPath">{{ event.httpMethod ?? '' }} {{ event.apiPath }}</template>
            <template v-else>-</template>
          </a-descriptions-item>
          <a-descriptions-item label="상태 코드">{{ event.httpStatus ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="오류 코드">{{ event.errorCode ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="오류 추적번호">{{ event.relatedCorrelationId ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="수집 추적번호">{{ event.ingestCorrelationId ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div v-if="event.stackSummary" class="section">
          <h4 class="section-title">스택 요약 <span class="sub">해시 {{ event.stackHash ?? '-' }}</span></h4>
          <pre class="json">{{ event.stackSummary }}</pre>
        </div>

        <a-descriptions title="세션·단말" :column="1" bordered size="small" class="section">
          <a-descriptions-item label="세션 ID">{{ event.clientSessionId }}</a-descriptions-item>
          <a-descriptions-item label="이벤트 ID">{{ event.clientEventId }}</a-descriptions-item>
          <a-descriptions-item label="브라우저">
            {{ event.browserName ?? '-' }} {{ event.browserVersion ?? '' }} / {{ event.osName ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="뷰포트 · 시간대">
            {{ event.viewport ?? '-' }} · {{ event.timezone ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="프론트 버전">{{ event.frontendVersion ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="IP 주소">{{ event.ipAddress ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="User-Agent">{{ event.userAgent ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사용자 가명키">{{ event.principalHash ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="사용자 유형">{{ event.principalType ?? '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="section">
          <h4 class="section-title">메타데이터</h4>
          <pre v-if="event.metadataJson" class="json">{{ prettyJson(event.metadataJson) }}</pre>
          <p v-else class="empty-text">없음</p>
        </div>
      </template>
    </a-spin>

    <template #footer>
      <div class="footer">
        <div class="actions">
          <a-button :disabled="!event" @click="goSession">같은 세션 이벤트 모두 보기</a-button>
          <a-button v-if="event?.applicationId != null" @click="goAudit">이 지원서 관련 감사 로그 보기</a-button>
        </div>
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

.sub {
  font-size: 12px;
  font-weight: 400;
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

.actions {
  display: flex;
  gap: 8px;
}
</style>
