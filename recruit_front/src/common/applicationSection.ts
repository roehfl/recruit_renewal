import type { sectionType } from '@/types/admin/application'

/*
 * 백엔드 sectionName 은 영문("Basic Info", "Questions")이라 화면에 그대로 쓸 수 없다.
 * sectionType 을 기준으로 한글 라벨을 매핑한다.
 */
export const SECTION_LABELS: Record<sectionType, string> = {
  BASIC_INFO: '기본정보',
  EDUCATION: '학력',
  CAREER: '경력',
  CERTIFICATE: '자격증',
  LANGUAGE: '어학',
  MILITARY: '병역',
  AWARD: '포상',
  GAP_PERIOD: '공백기간',
  QUESTION_ANSWER: '자기소개서',
  ATTACHMENT: '첨부파일',
}

/** availableSections[].source — 이 섹션의 사용 여부를 어디서 정하는지 안내한다. */
export const SECTION_SOURCE_LABELS: Record<string, string> = {
  ALWAYS: '항상 포함',
  APPLICATION_FORM_CONFIG: '지원서 양식 탭',
  QUESTION: '자기소개서 질문',
  ATTACHMENT_REQUIREMENT: '첨부 요구사항',
}

export const sectionLabel = (type: sectionType): string => SECTION_LABELS[type] ?? type

export const sectionSourceLabel = (source: string): string => SECTION_SOURCE_LABELS[source] ?? source
