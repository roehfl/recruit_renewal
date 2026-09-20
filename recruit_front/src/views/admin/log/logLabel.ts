import type { ClientEventSeverity, ClientEventType } from '@/types/clientEvent'
import type {
  AuditActionResult,
  AuditActionType,
  AuditActorType,
  AuditTargetType,
} from '@/types/admin/auditLog'

/*
 * 로그 조회 화면의 코드 → 한글 라벨. 목록·상세·필터 select 가 같은 라벨을 써야 해서 한 곳에 모은다.
 * 백엔드 enum 에 값이 늘면 여기와 옵션 개수 테스트를 같이 고친다.
 */

export interface LogTag {
  label: string
  color: string
}

const toOptions = <T extends string>(labels: Record<T, string>): { value: T; label: string }[] =>
  (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }))

const ACTION_TYPE_LABEL: Record<AuditActionType, string> = {
  EXPORT_APPLICATIONS: '지원현황 엑셀 반출',
  EXPORT_STAGE_RESULTS: '전형결과 반출',
  EXPORT_INTERVIEWS: '면접 명단 반출',
  EXPORT_EVALUATIONS: '면접평가 반출',
  EXPORT_STAGE_RESULT_TEMPLATE: '업로드 양식 반출',
  APPLICATION_PDF: '지원서 PDF 생성',
  STAGE_RESULT_UPLOAD: '전형결과 업로드',
  STAGE_RESULT_CORRECT: '전형결과 정정',
  STAGE_RESULT_ANNOUNCE: '전형결과 발표',
  STAGE_RESULT_CONFIRM: '전형결과 확정',
  EVALUATION_REOPEN: '면접평가 재오픈',
  ATTACHMENT_ADMIN_DOWNLOAD: '첨부파일 다운로드',
  ATTACHMENT_ADMIN_DELETE: '첨부파일 삭제',
  RETENTION_POLICY_UPDATE: '보존정책 변경',
  RETENTION_HOLD_SET: '보존 보류 설정',
  RETENTION_HOLD_RELEASE: '보존 보류 해제',
  RETENTION_ANCHOR_SET: '보존 기준일 설정',
  PURGE_SCAN: '파기 대상 산정',
  PURGE_EXECUTE: '파기 실행',
  PURGE_RECONCILE: '파기 재처리',
  PURGE_FORCED: '강제 파기',
}

const TARGET_TYPE_LABEL: Record<AuditTargetType, string> = {
  STAGE_RESULT: '전형결과',
  JOB_APPLICATION: '지원서',
  APPLICATION_ATTACHMENT: '첨부파일',
  INTERVIEW_EVALUATION: '면접평가',
  EXPORT_DATASET: '반출 데이터셋',
  APPLICATION_PDF: '지원서 PDF',
  RETENTION_POLICY: '보존정책',
  RETENTION_HOLD: '보존 보류',
  JOB_POSTING: '공고',
  PURGE_BATCH: '파기 배치',
  RETENTION_SCHEDULE: '파기 스케줄',
}

const ACTOR_TYPE_LABEL: Record<AuditActorType, string> = {
  EMPLOYEE: '직원',
  SYSTEM: '시스템',
  APPLICANT: '지원자',
  ANONYMOUS: '비로그인',
}

const ACTION_RESULT_TAG: Record<AuditActionResult, LogTag> = {
  SUCCESS: { label: '성공', color: 'green' },
  FAILURE: { label: '실패', color: 'red' },
  DENIED: { label: '거부', color: 'red' },
  SKIPPED: { label: '건너뜀', color: 'default' },
  CONFLICT: { label: '충돌', color: 'orange' },
}

const EVENT_TYPE_LABEL: Record<ClientEventType, string> = {
  PAGE_OPENED: '화면 진입',
  CHECKPOINT: '체크포인트',
  API_ERROR: 'API 오류',
  API_TIMEOUT: 'API 타임아웃',
  NETWORK_ERROR: '네트워크 오류',
  SESSION_EXPIRED: '세션 만료',
  FORBIDDEN: '권한 없음',
  JS_ERROR: '스크립트 오류',
  UNHANDLED_REJECTION: '미처리 Promise',
  APPLICATION_DRAFT_SAVE_FAILED: '임시저장 실패',
  APPLICATION_SUBMIT_CLICKED: '제출 클릭',
  APPLICATION_SUBMIT_FAILED: '제출 실패',
  ATTACHMENT_UPLOAD_FAILED: '첨부 업로드 실패',
  CLIENT_VALIDATION_FAILED: '입력검증 실패',
}

const SEVERITY_COLOR: Record<ClientEventSeverity, string> = {
  ERROR: 'red',
  WARN: 'orange',
  INFO: 'blue',
}

export const actionTypeLabel = (value: AuditActionType): string => ACTION_TYPE_LABEL[value]
export const targetTypeLabel = (value: AuditTargetType): string => TARGET_TYPE_LABEL[value]
export const actorTypeLabel = (value: AuditActorType): string => ACTOR_TYPE_LABEL[value]
export const actionResultTag = (value: AuditActionResult): LogTag => ACTION_RESULT_TAG[value]
export const eventTypeLabel = (value: ClientEventType): string => EVENT_TYPE_LABEL[value]
export const severityColor = (value: ClientEventSeverity): string => SEVERITY_COLOR[value]

export const ACTION_TYPE_OPTIONS = toOptions(ACTION_TYPE_LABEL)
export const TARGET_TYPE_OPTIONS = toOptions(TARGET_TYPE_LABEL)
export const EVENT_TYPE_OPTIONS = toOptions(EVENT_TYPE_LABEL)
export const ACTION_RESULT_OPTIONS = (Object.keys(ACTION_RESULT_TAG) as AuditActionResult[]).map((value) => ({
  value,
  label: ACTION_RESULT_TAG[value].label,
}))
export const SEVERITY_OPTIONS = (Object.keys(SEVERITY_COLOR) as ClientEventSeverity[]).map((value) => ({
  value,
  label: value,
}))

/** metadataJson 을 상세에서 읽기 좋게 편다. 파싱 실패하면 원문 그대로. */
export const prettyJson = (value: string | null): string => {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
