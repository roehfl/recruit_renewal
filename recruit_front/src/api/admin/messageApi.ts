import { apiClient } from '../client'
import type { ApiResponse } from '@/types/api'
import type {
  MessageHistoryQuery,
  MessageSendDetail,
  MessageSendRequest,
  MessageSendResult,
  MessageSendSummary,
  MessageTargetQuery,
  MessageTargetResponse,
  MessageTemplate,
  MessageTemplateSaveRequest,
  MessageTestSendRequest,
  MessageTestSendResponse,
  MessageType,
  MessageVariable,
} from '@/types/admin/message'
import type { PageResponse } from '@/types/page'

// 테스트 발송은 게이트웨이를 동기로 호출하고, 실제 발송은 최대 3,000명 기록을 한 번에 저장한다. 기본 10초로는 부족할 수 있다.
const SEND_TIMEOUT_MS = 60000

/** 메시지 발송 기능 API 모듈. 템플릿·변수·대상자 조회·테스트 발송·발송 접수·발송 이력. */
export const messageApi = {
  /** 변수 카탈로그. 종류별 허용 여부를 담는다. */
  getVariables() {
    return apiClient.get<ApiResponse<MessageVariable[]>>('/admin/messages/variables')
  },

  /** 종류·조건별 수신 대상과 수신자별 변수 값, 발신 정보. 선택 불가 조건은 400. */
  getTargets(query: MessageTargetQuery) {
    return apiClient.get<ApiResponse<MessageTargetResponse>>('/admin/messages/targets', { params: query })
  },

  /** 테스트 발송. 미리보기 대상의 값으로 치환해 담당자에게 바로 보내고 채널별 결과를 돌려준다. */
  testSend(request: MessageTestSendRequest) {
    return apiClient.post<ApiResponse<MessageTestSendResponse>>('/admin/messages/test', request, {
      timeout: SEND_TIMEOUT_MS,
    })
  },

  /** 실제 발송 접수. 발송은 서버에서 비동기로 진행되고 결과는 발송 이력에서 본다. */
  send(request: MessageSendRequest) {
    return apiClient.post<ApiResponse<MessageSendResult>>('/admin/messages/send', request, {
      timeout: SEND_TIMEOUT_MS,
    })
  },

  /** 발송 이력 목록(발송일시 최신순). 기간을 비우면 서버 기본 최근 30일. 상태·건수는 조회 때 계산된다. */
  getHistory(query: MessageHistoryQuery) {
    return apiClient.get<ApiResponse<PageResponse<MessageSendSummary>>>('/admin/messages/history', {
      params: query,
    })
  },

  /** 발송 1회의 원문·집계·수신자별 결과. 테스트 발송 카드와 이력 드로어가 결과 수신 중일 때 다시 읽는다. */
  getHistoryDetail(sendId: number) {
    return apiClient.get<ApiResponse<MessageSendDetail>>(`/admin/messages/history/${sendId}`)
  },

  /** 템플릿 목록. 종류 → 기본 우선 → 이름순. type 을 주면 그 종류만. */
  getTemplates(type?: MessageType) {
    return apiClient.get<ApiResponse<MessageTemplate[]>>('/admin/message-templates', {
      params: { type },
    })
  },

  /** 기본으로 저장하면 같은 종류의 기존 기본 템플릿이 해제된다. */
  createTemplate(request: MessageTemplateSaveRequest) {
    return apiClient.post<ApiResponse<MessageTemplate>>('/admin/message-templates', request)
  },

  updateTemplate(templateId: number, request: MessageTemplateSaveRequest) {
    return apiClient.post<ApiResponse<MessageTemplate>>(`/admin/message-templates/${templateId}`, request)
  },

  /** 발송 이력은 템플릿 이름·원문을 복사해 두므로 삭제해도 영향이 없다. */
  deleteTemplate(templateId: number) {
    return apiClient.post<ApiResponse<null>>(`/admin/message-templates/${templateId}/delete`)
  },
}
