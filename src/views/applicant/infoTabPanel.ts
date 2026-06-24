// 복리후생/교육/평가 안내 화면 공통 데이터 모델

export interface CardItem {
  label: string
  desc?: string
}

export interface Chip {
  key: string
  label: string
  items: CardItem[]
}

export interface PanelTab {
  key: string
  label: string
  /** chips[0]이 기본 선택('전체') */
  chips: Chip[]
}
