import type { AnyMessageType, MessageType, SystemMessageType } from '@/types/admin/message'

export interface MessageTypeMeta<T extends AnyMessageType = MessageType> {
  type: T
  /** 화면 묶음 라벨(공고 관련·면접 안내·기타·시스템 자동발송). 표시용이다. */
  group: string
  name: string
  description: string
}

/** 표시 순서 = 발송 화면 종류 카드 순서(설계서 2절). 관리자가 직접 보내는 종류만 담는다. */
export const MESSAGE_TYPES: MessageTypeMeta[] = [
  { type: 'RESULT_ANNOUNCEMENT', group: '공고 관련', name: '결과 발표', description: '전형 결과 발표 후 안내' },
  { type: 'DEADLINE_REMINDER', group: '공고 관련', name: '서류 마감 임박', description: '미제출 지원자에게 리마인드' },
  { type: 'INTERVIEW_SCHEDULE', group: '면접 안내', name: '면접 일정·장소', description: '배정된 일시·장소 개별 안내' },
  { type: 'INTERVIEW_NOTICE', group: '면접 안내', name: '면접 공지', description: '준비물·유의사항·변경 공지' },
  { type: 'FREE', group: '기타', name: '직접 입력', description: '내용을 자유롭게 작성' },
]

/** 시스템 자동발송 종류. 템플릿·발송 이력 화면에만 나오고 발송 화면에서는 고를 수 없다. */
export const SYSTEM_MESSAGE_TYPES: MessageTypeMeta<SystemMessageType>[] = [
  { type: 'SIGNUP_VERIFICATION', group: '시스템 자동발송', name: '회원가입 인증', description: '가입 이메일 인증번호' },
  { type: 'PASSWORD_RESET', group: '시스템 자동발송', name: '비밀번호 재설정 인증', description: '비밀번호 재발급 인증번호' },
  { type: 'APPLICATION_SUBMITTED', group: '시스템 자동발송', name: '지원서 제출 완료', description: '최종 제출·재제출 안내' },
]

/** 템플릿·발송 이력 화면용 전체 종류(관리자 종류 → 시스템 종류). */
export const ALL_MESSAGE_TYPES: MessageTypeMeta<AnyMessageType>[] = [...MESSAGE_TYPES, ...SYSTEM_MESSAGE_TYPES]

export const isSystemMessageType = (type: AnyMessageType): type is SystemMessageType =>
  SYSTEM_MESSAGE_TYPES.some((meta) => meta.type === type)

export const messageTypeLabel = (type: AnyMessageType): string => {
  const meta = ALL_MESSAGE_TYPES.find((item) => item.type === type)
  return meta ? `${meta.group} · ${meta.name}` : type
}
