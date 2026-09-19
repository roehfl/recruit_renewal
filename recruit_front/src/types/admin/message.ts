/*
 * 메시지(메일·SMS) 타입. 백엔드 MessageTemplateResponse / MessageVariableResponse,
 * 대상자·발송·발송 이력 관련 요청·응답 DTO 와 대응한다(docs/domains/message.md "API 계약").
 */
import type { StageResultStatus } from '@/types/admin/stage'

export type MessageType =
  | 'RESULT_ANNOUNCEMENT'
  | 'DEADLINE_REMINDER'
  | 'INTERVIEW_SCHEDULE'
  | 'INTERVIEW_NOTICE'
  | 'FREE'

export interface MessageTemplate {
  id: number
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
  updatedAt: string
}

/** 등록·수정 공용. 빈 채널은 null 로 보낸다. 메일은 제목·본문을 함께 채우거나 함께 비운다. */
export interface MessageTemplateSaveRequest {
  type: MessageType
  name: string
  defaultTemplate: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
}

/** 본문 변수. key 가 #{key} 로 쓰이고, types 에 든 종류에서만 쓸 수 있다. */
export interface MessageVariable {
  key: string
  label: string
  types: MessageType[]
}

/** 대상자 조회 조건. 값이 없는 키는 "조건 없음"(결과 전체·전체 조·작성 중+제출). */
export interface MessageTargetQuery {
  type: MessageType
  jobPostingId: number
  stageId?: number
  resultStatus?: StageResultStatus
  interviewGroup?: string
  applicationStatus?: 'DRAFT' | 'SUBMITTED'
}

/** 수신 대상 1명(지원서 1건). 연락처는 원문, 형식이 맞을 때만 *Available 이 true. */
export interface MessageTargetRecipient {
  applicationId: number
  name: string | null
  email: string | null
  phone: string | null
  mailAvailable: boolean
  smsAvailable: boolean
  /** 결과 발표에서만 */
  resultStatus: StageResultStatus | null
  /** 면접 2종에서만 */
  interviewGroup: string | null
  interviewDateTime: string | null
  /** 서류 마감 임박에서만(지원서 작성 시작 시각) */
  draftStartedAt: string | null
  /** 그 종류에 허용된 변수의 수신자별 값 */
  variables: Record<string, string>
  /** 값이 빈 변수 키 */
  missingVariables: string[]
}

export interface MessageSender {
  name: string
  email: string
  smsCallbackNumber: string
}

export interface MessageTargetResponse {
  recipients: MessageTargetRecipient[]
  /** 면접 2종에서만: 확정 면접의 조 목록 */
  interviewGroups: string[]
  sender: MessageSender
}

/** 발송 화면에서 작성 중인 내용. 템플릿에서 불러오며 여기서 고친 내용은 이번 발송에만 적용된다. */
export interface MessageContent {
  templateId: number | null
  mailEnabled: boolean
  smsEnabled: boolean
  mailSubject: string
  mailBody: string
  smsBody: string
}

export type MessageChannel = 'MAIL' | 'SMS'
/** 수신자·채널별 결과. REQUESTED = 솔루션 접수(결과 수신 중), 최종은 SENT·FAILED. SKIPPED 는 보내지 않음. */
export type MessageDeliveryStatus = 'PENDING' | 'REQUESTED' | 'SENT' | 'FAILED' | 'SKIPPED'
/** 발송 1회 상태. 서버가 조회할 때 수신자 채널 상태로 계산한다. */
export type MessageSendStatus = 'SENDING' | 'RESULT_PENDING' | 'COMPLETED'

/** 발송·테스트 발송 요청의 작성 내용(치환 전). 켠 채널의 필수 입력은 서버도 검증한다. */
export interface MessageContentRequest {
  templateId: number | null
  mailEnabled: boolean
  smsEnabled: boolean
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
}

/** 테스트 발송 수신자(인사팀 담당자). 이메일·휴대폰 중 1개 이상. */
export interface MessageTester {
  name: string
  email: string | null
  phone: string | null
}

/** 실제 발송. 조건은 대상자를 조회했던 쿼리 그대로 보낸다. */
export interface MessageSendRequest extends MessageTargetQuery {
  applicationIds: number[]
  content: MessageContentRequest
}

export interface MessageTestSendRequest extends MessageTargetQuery {
  previewApplicationId: number
  testers: MessageTester[]
  content: MessageContentRequest
}

export interface MessageSendResult {
  sendId: number
  status: MessageSendStatus
  recipientCount: number
  /** 요청했지만 조건이 바뀌어 빠진 인원 */
  excludedCount: number
}

export interface MessageTestSendResultItem {
  name: string
  channel: MessageChannel
  /** 응답 시점에는 접수 결과(REQUESTED·FAILED·SKIPPED). 최종 결과는 발송 이력 상세로 갱신한다. */
  status: MessageDeliveryStatus
  /** NO_CONTACT · INVALID_CONTACT · CHANNEL_OFF · GATEWAY_ERROR 또는 솔루션 결과코드 */
  failureReason: string | null
}

export interface MessageTestSendResponse {
  sendId: number
  results: MessageTestSendResultItem[]
}

/** 발송 이력 조회 조건. 날짜는 YYYY-MM-DD(양끝 포함). 값이 없는 키는 조건 없음(기간은 서버 기본 최근 30일). */
export interface MessageHistoryQuery {
  from?: string
  to?: string
  type?: MessageType
  jobPostingId?: number
  /** 없으면 실발송+테스트, true 테스트만, false 실발송만 */
  test?: boolean
  page: number
  size: number
}

/** 채널 하나의 상태별 수신자 수. */
export interface MessageChannelCount {
  pending: number
  requested: number
  sent: number
  failed: number
  skipped: number
}

/** 발송 이력 목록 1행. status·건수·delayed 는 서버가 조회할 때 계산한다. */
export interface MessageSendSummary {
  id: number
  requestedAt: string
  type: MessageType
  test: boolean
  jobPostingTitle: string
  stageName: string | null
  conditionSummary: string | null
  /** 메일 제목. 메일을 끈 발송은 SMS 원문 앞 40자 */
  title: string | null
  mailEnabled: boolean
  smsEnabled: boolean
  recipientCount: number
  mail: MessageChannelCount
  sms: MessageChannelCount
  status: MessageSendStatus
  /** 요청 후 결과 대기 시간(기본 60분)이 지났는데 완료가 아님 */
  delayed: boolean
  senderName: string | null
}

/** 이력 상세의 수신자 1명. 연락처는 원문. 파기된 수신자는 name·email·phone 이 null. */
export interface MessageHistoryRecipient {
  id: number
  /** 테스트 수신자(담당자)는 null */
  applicationId: number | null
  name: string | null
  email: string | null
  phone: string | null
  mailStatus: MessageDeliveryStatus
  mailFailureReason: string | null
  smsStatus: MessageDeliveryStatus
  smsFailureReason: string | null
  smsKind: 'SMS' | 'LMS' | null
}

/** 발송 이력 상세 = 목록 필드 + 치환 전 원문 + 수신자별 결과(id 순). */
export interface MessageSendDetail extends MessageSendSummary {
  templateName: string | null
  mailSubject: string | null
  mailBody: string | null
  smsBody: string | null
  recipients: MessageHistoryRecipient[]
}
