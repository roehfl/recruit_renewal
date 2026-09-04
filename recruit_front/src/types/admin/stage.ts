/*
 * 전형 단계(Stage)와 전형 결과(StageResult) 타입.
 * 백엔드 StageListResponse / AdminStageResultResponse 와 대응한다(api-contract.md "전형결과 관리").
 */

/** 단계 라이프사이클. READY → IN_PROGRESS → RESULT_ANNOUNCED → CLOSED */
export type StageStatus = 'READY' | 'IN_PROGRESS' | 'RESULT_ANNOUNCED' | 'CLOSED'

export type StageType = 'DOCUMENT' | 'FIRST_INTERVIEW' | 'SECOND_INTERVIEW' | 'FINAL_INTERVIEW' | 'ETC'

/** 전형 결과 상태. PENDING 은 초기화 직후 값이며 판정으로 지정할 수 없다. */
export type StageResultStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'ABSENT' | 'WITHDRAWN' | 'HOLD'

export type EducationLevel = 'HIGH_SCHOOL' | 'COLLEGE' | 'UNIVERSITY' | 'MASTER' | 'DOCTOR'

export type JobPositionApplicationType = 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'

export interface StageListItem {
  id: number
  jobPostingId: number
  stageName: string
  stageType: StageType
  stageOrder: number
  status: StageStatus
  /** 발표 예정 일시. 미정이면 null */
  resultAnnouncementDateTime: string | null
  finalStage: boolean
}

/** 전형 결과 한 행. 뒤쪽 6개는 그리드 표시용 파생 필드다(2026-09-04 백엔드 확장). */
export interface AdminStageResult {
  stageResultId: number
  stageId: number
  applicationId: number
  applicantName: string
  jobPositionId: number
  jobPositionName: string
  applicationStatus: 'DRAFT' | 'SUBMITTED' | 'WITHDRAWN'
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
  submittedAt: string | null
  decidedAt: string | null
  /** 판정자 로그인 id. 미판정이면 null */
  decidedBy: string | null
  /** 지원자가 선택한 근무지 표시명. 근무지 후보가 없는 모집분야면 null */
  workLocation: string | null
  applicationType: JobPositionApplicationType
  finalEducationLevel: EducationLevel | null
  finalSchoolName: string | null
  /** stageOrder 가 바로 앞인 단계의 결과. 첫 단계이거나 그 단계에 결과 행이 없으면 null */
  previousStageResultStatus: StageResultStatus | null
}

/** 판정 편집 버퍼의 한 항목. 저장 전 값이며 원본과 같아지면 본체가 항목을 지운다. */
export interface PendingEdit {
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
}

export interface StageResultBulkUpdateItem {
  stageResultId: number
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
}

export interface StageResultBulkUpdateRequest {
  results: StageResultBulkUpdateItem[]
}

export interface StageResultBulkUpdateResponse {
  stageId: number
  updatedCount: number
  results: AdminStageResult[]
}

export interface StageResultInitializeResponse {
  stageId: number
  createdCount: number
  existingCount: number
  skippedCount: number
  results: AdminStageResult[]
}

/*
 * 결과 상태 한글 라벨. 백엔드 StageResultStatusLabels 와 **글자까지 같아야 한다** —
 * 엑셀 템플릿의 드롭다운 값이 이 표에서 나오므로, 한쪽만 바꾸면 엑셀과 화면의 단어가 갈라진다.
 */
export const STAGE_RESULT_STATUS_LABELS: Record<StageResultStatus, string> = {
  PENDING: '대기',
  PASSED: '합격',
  FAILED: '불합격',
  ABSENT: '결시',
  WITHDRAWN: '철회',
  HOLD: '보류',
}

/** 배지 색. ant-design-vue a-tag 의 프리셋 색 이름이다. */
export const STAGE_RESULT_STATUS_COLORS: Record<StageResultStatus, string> = {
  PENDING: 'orange',
  PASSED: 'green',
  FAILED: 'red',
  ABSENT: 'default',
  WITHDRAWN: 'default',
  HOLD: 'blue',
}

/** 판정으로 지정 가능한 값 = 전체 − PENDING. 셀렉트 옵션 순서다. */
export const DECIDABLE_RESULT_STATUSES: StageResultStatus[] = [
  'PASSED',
  'FAILED',
  'HOLD',
  'ABSENT',
  'WITHDRAWN',
]

/** 선택 행 일괄 적용 버튼. 철회는 개별 판단이 필요해 버튼에서 제외한다(행 셀렉트로만 지정). */
export const BULK_APPLY_STATUSES: StageResultStatus[] = ['PASSED', 'FAILED', 'HOLD', 'ABSENT']

export const STAGE_STATUS_LABELS: Record<StageStatus, string> = {
  READY: '대기',
  IN_PROGRESS: '진행중',
  RESULT_ANNOUNCED: '발표완료',
  CLOSED: '마감',
}

export const EDUCATION_LEVEL_LABELS: Record<EducationLevel, string> = {
  HIGH_SCHOOL: '고등학교',
  COLLEGE: '전문대학교',
  UNIVERSITY: '대학교',
  MASTER: '대학원(석사)',
  DOCTOR: '대학원(박사)',
}

export const APPLICATION_TYPE_LABELS: Record<JobPositionApplicationType, string> = {
  NEW_GRADUATE: '신입',
  EXPERIENCED: '경력',
  NEW_GRADUATE_OR_EXPERIENCED: '신입/경력',
}

/* ---- 엑셀 업로드 (S3) ---- */

/** 업로드 행 상태. STALE 은 commit 시점의 낙관적 동시성 위반이라 preview 에는 나오지 않는다. */
export type StageResultUploadRowStatus = 'CHANGED' | 'UNCHANGED' | 'ERROR' | 'STALE'

/** all-or-nothing 결과. REJECTED_* 는 0건 적용이다. */
export type StageResultUploadCommitOutcome = 'APPLIED' | 'REJECTED_VALIDATION' | 'REJECTED_STALE'

/**
 * 변경 전후 비교. **모든 값이 문자열**이고 결과는 enum 이름(`PASSED` 등)으로 온다 —
 * 화면에 그릴 때 STAGE_RESULT_STATUS_LABELS 로 바꿔야 한다. 값이 없으면 null.
 */
export interface StageResultUploadDiff {
  oldResultStatus: string | null
  newResultStatus: string | null
  oldScore: string | null
  newScore: string | null
  oldComment: string | null
  newComment: string | null
}

export interface StageResultUploadRow {
  /** 스프레드시트 행 번호(1-based, 헤더가 1행) */
  rowNumber: number
  /** 파싱 실패 시 null */
  stageResultId: number | null
  applicationId: number | null
  applicantName: string | null
  status: StageResultUploadRowStatus
  /** ERROR·STALE 사유. 그 외엔 빈 배열 */
  errors: string[]
  /** CHANGED·STALE 만 값이 있다 */
  diff: StageResultUploadDiff | null
}

export interface StageResultUploadPreview {
  stageId: number
  totalRows: number
  changedCount: number
  unchangedCount: number
  errorCount: number
  /** errorCount === 0. 동시성 위반은 commit 에서 따로 판정하므로 이 값이 true 여도 거부될 수 있다. */
  committable: boolean
  rows: StageResultUploadRow[]
}

export interface StageResultUploadCommit {
  stageId: number
  outcome: StageResultUploadCommitOutcome
  totalRows: number
  changedCount: number
  unchangedCount: number
  errorCount: number
  staleCount: number
  /** 거부 시 ERROR·STALE 행. 성공 시 빈 배열 */
  failedRows: StageResultUploadRow[]
}

/**
 * 백엔드가 400·409 로 거부하면서도 본문 data 에 결과를 담아 주는 응답의 모양.
 * 엑셀 commit 이 이 형태다 — 거부여도 failedRows 를 꺼내 행별 사유를 보여줄 수 있다.
 */
export interface ApiFailurePayload<T> {
  success: boolean
  message?: string
  data: T | null
}

/* ---- 발표 후 정정 (S3) ---- */

export interface StageResultCorrectionRequest {
  resultStatus: StageResultStatus
  score: number | null
  comment: string | null
  /** 필수, 1000자 이하. 이력에 남는다. */
  reason: string
}

export interface StageResultCorrectionHistory {
  historyId: number
  stageResultId: number
  correctedAt: string
  correctedBy: string
  reason: string
  previousStatus: StageResultStatus
  newStatus: StageResultStatus
  previousScore: number | null
  newScore: number | null
  previousComment: string | null
  newComment: string | null
  previousDecidedAt: string | null
  newDecidedAt: string | null
}

/* ---- 단계 설정 (S4) ---- */

/**
 * 단계 생성·수정 요청. 백엔드가 두 요청에 같은 모양을 쓴다.
 *
 * 진행 중(IN_PROGRESS) 단계를 수정할 때도 잠긴 4개 필드를 **현재 값 그대로** 실어야 한다 —
 * 형식 검증(@NotBlank/@NotNull)이 완화 분기보다 먼저 돌아서 비우면 400 이다(계약 변경 2).
 */
export interface StageSaveRequest {
  stageName: string
  stageType: StageType
  stageOrder: number
  /** 발표 예정 일시. 미정이면 null */
  resultAnnouncementDateTime: string | null
  finalStage: boolean
}

/**
 * 순서 일괄 변경 요청. **공고의 모든 단계를 빠짐없이** 보내야 하고,
 * 하나라도 READY 가 아니면 백엔드가 400 으로 거부한다.
 */
export interface StageReorderRequest {
  items: { stageId: number; stageOrder: number }[]
}

export const STAGE_TYPE_LABELS: Record<StageType, string> = {
  DOCUMENT: '서류',
  FIRST_INTERVIEW: '1차 면접',
  SECOND_INTERVIEW: '2차 면접',
  FINAL_INTERVIEW: '최종 면접',
  ETC: '기타',
}

/** 드로어가 새 단계에 부여하는 순서 간격. 중간 삽입 여지를 남긴다(설계 §3.6). */
export const STAGE_ORDER_STEP = 10
