import type { MessageType } from '@/types/admin/message'

export interface MessageTypeMeta {
  type: MessageType
  /** 화면 묶음 라벨(공고 관련·면접 안내·기타). 표시용이다. */
  group: string
  name: string
  description: string
}

/** 표시 순서 = 발송 화면 종류 카드 순서(설계서 2절). */
export const MESSAGE_TYPES: MessageTypeMeta[] = [
  { type: 'RESULT_ANNOUNCEMENT', group: '공고 관련', name: '결과 발표', description: '전형 결과 발표 후 안내' },
  { type: 'DEADLINE_REMINDER', group: '공고 관련', name: '서류 마감 임박', description: '미제출 지원자에게 리마인드' },
  { type: 'INTERVIEW_SCHEDULE', group: '면접 안내', name: '면접 일정·장소', description: '배정된 일시·장소 개별 안내' },
  { type: 'INTERVIEW_NOTICE', group: '면접 안내', name: '면접 공지', description: '준비물·유의사항·변경 공지' },
  { type: 'FREE', group: '기타', name: '직접 입력', description: '내용을 자유롭게 작성' },
]

export const messageTypeLabel = (type: MessageType): string => {
  const meta = MESSAGE_TYPES.find((item) => item.type === type)
  return meta ? `${meta.group} · ${meta.name}` : type
}
