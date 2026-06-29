export interface DutySkill {
  /** 굵게 표시될 선두 문구 (예: "공감 능력 및 커뮤니케이션 역량:") */
  lead: string
  /** 이어지는 설명 */
  body: string
}

export interface DutyItem {
  /** 모달 open/close 상태 매핑 키 (기존 url 값 유지) */
  url: string
  /** 헤더 영문 라벨 (eyebrow) */
  eyebrow: string
  /** 국문 타이틀 */
  title: string
  /** 필요역량 — 전공 pill 값 */
  major: string
  /** 필요역량 — 학위 pill 값 */
  degree: string
  /** ① 직무소개 */
  intro: string
  /** ② 필요역량 항목 리스트 */
  skills: DutySkill[]
  /** ③ 커리어패스 */
  career: string
  /** 신영증권만의 차별점 */
  diff: string
}
