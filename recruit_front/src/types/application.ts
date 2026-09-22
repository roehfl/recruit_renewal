export type ApplicationSectionType =
  | 'BASIC_INFO'
  | 'MILITARY'
  | 'EDUCATION'
  | 'CAREER'
  | 'CERTIFICATE'
  | 'LANGUAGE'
  | 'AWARD'
  | 'GAP_PERIOD'
  | 'QUESTION_ANSWER'
  | 'ATTACHMENT'

export type applicationStatus = 
  | 'DRAFT'
  | 'SUBMITTED'
  | 'WITHDRAWN'

export type latestResultStatus =
  | 'PENDING'

export type postingType =
  | 'PUBLIC_RECRUITMENT'
  | 'EXPERIENCED_RECRUITMENT'
  | 'INTERN_RECRUITMENT'
  | 'ROLLING_RECRUITMENT'

export interface ApplicationFormItem {
  sectionType: ApplicationSectionType
  sectionName?: string
  required: boolean
  sortOrder: number
}

export interface ApplicationFormPage {
  pageNo: number
  title: string
  description?: string | null
  sortOrder: number
  items: ApplicationFormItem[]
}

export interface ApplicationFormPageResponse {
  applicationId: number
  jobPostingId: number
  jobPostingTitle?: string
  postingTitle?: string
  postingType?: postingType
  jobPositionId?: number
  jobPositionName?: string
  /** 지원자가 선택한 근무지 코드. 근무지 후보가 없는 모집분야면 null. */
  workLocationCode?: string | null
  /** 지원자가 선택한 근무지 표시명. 근무지 후보가 없는 모집분야면 null. */
  workLocationName?: string | null
  accepting?: boolean
  editable?: boolean
  status?: string
  applicationStatus?: string
  pages?: ApplicationFormPage[]
  sections?: ApplicationFormItem[]
}

export interface SectionActionHandle {
  saveDraft?: () => Promise<void> | void
  validateBeforeSubmit?: () => Promise<boolean> | boolean
  /** 마지막 조회·저장 이후 입력이 바뀌었는지. 페이지 이동·제출 전 자동 임시저장 판단에 쓴다. */
  isDirty?: () => boolean
}

export interface SectionComponentProps {
  applicationId: number
  section: ApplicationFormItem
  page: ApplicationFormPage
  editable: boolean
  formPage: ApplicationFormPageResponse
}

export interface MyApplicationListItem {
  applicationId: number
  jobPostingId: number
  jobPostingTitle?: string
  postingTitle?: string
  jobPositionId?: number
  jobPositionName?: string
  applicationStatus?: applicationStatus
  createdAt?: string
  submittedAt?: string
  withdrawnAt?: string 
  receptionStartDateTime?: string
  receptionEndDateTime?: string
  accepting?: boolean
  announcedResultCount?: number
  latestAnnouncedStageName?: string
  latestResultStatus?: string
}

export interface MyApplicationList {
  content: MyApplicationListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApplicationSearchParams {
  page: number
  size: number
}

export interface ChangePasswordParams {
  currentPassword: string
  newPassword: string
}

export interface ChangePasswordRequest {
  data: string
  message: string
  success: boolean
}

/**
 * 가입 요청 본문. 이름·휴대폰·CI 는 없다 — 서버가 NICE 본인확인 결과를 세션에서 꺼내 쓴다.
 * 클라이언트가 보낸 값을 믿으면 본인확인이 무의미해지고 CI 중복 차단도 뚫린다.
 */
export interface SignupUser {
  loginId: string,
  password: string,
  email: string
}

export interface checkEmailRequest {
  success: boolean,
  data: {available: boolean},
  message: string
}

/** POST /auth/applicants/find-email — 세션의 NICE 인증 결과로 찾은 아이디(부분 마스킹). 원문은 오지 않는다. */
export interface FindEmailResponse {
  maskedEmail: string
}

/** GET /applications/{applicationId}/stage-results — 발표된 전형 결과만 내려온다(임시저장 지원서는 400). */
export interface ApplicantStageResult {
  stageName: string
  stageType: string
  stageOrder: number
  resultStatus: 'PENDING' | 'PASSED' | 'FAILED' | 'ABSENT' | 'WITHDRAWN' | 'HOLD'
  resultAnnouncementDateTime: string | null
  decidedAt: string | null
}
