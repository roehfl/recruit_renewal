export type method = 'IN_PERSON' | 'ONLINE' | 'HYBRID' | 'OTHER'

export interface InterviewCreateRequest {
  stageId: number | null
  groupName: string | undefined
  startDateTime: string | undefined
  /** 지원자 도착 시각. 면접 시각보다 늦을 수 없다(종료 시각은 다루지 않는다) */
  arrivalDateTime?: string
  method: method
  locationName?: string
  roomName?: string
  onlineMeetingUrl?: string
  memo?: string
}

/** 면접 스케줄 조회·다운로드 조건. stageId 필수, 나머지는 지원자 행에 거는 선택 필터 */
export interface InterviewScheduleSearchParams {
  stageId: number
  applicationType?: string
  jobPositionId?: number
  /** 근무지 코드 */
  workLocation?: string
  /** 조. 정확히 일치 */
  groupName?: string
}

export interface AdminInterviewScheduleInterviewer {
  employeeId: number
  name: string
  loginId: string
}

/** 면접 스케줄 한 행 = 조(면접)에 배정된 지원자 1명. 조 단위 값은 같은 조의 행마다 반복된다 */
export interface AdminInterviewScheduleRow {
  interviewId: number
  groupName: string
  /** 면접순서 */
  candidateOrder: number | null
  /** 면접 시각 */
  interviewDateTime: string
  arrivalDateTime: string | null
  locationName: string | null
  interviewers: AdminInterviewScheduleInterviewer[]
  /** 수험번호 */
  applicationId: number
  applicantName: string | null
}

export interface InterviewScheduleUploadRowError {
  /** 엑셀 기준 행 번호(헤더 = 1행) */
  rowNumber: number
  messages: string[]
}

/** 업로드 결과. 오류가 1건이라도 있으면 400 이고 건수는 0, 아무것도 반영되지 않는다 */
export interface InterviewScheduleUploadResponse {
  stageId: number
  interviewCount: number
  candidateCount: number
  replacedInterviewCount: number
  /** 행에 묶이지 않는 파일 단위 오류(조 번호 누락, 면접관 시각 중복 등) */
  errors: string[]
  rowErrors: InterviewScheduleUploadRowError[]
}
