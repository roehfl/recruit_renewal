/*
 * 대시보드 차트 팔레트.
 *
 * 아래 값은 실제 카드 서피스(#ffffff)에 대해 검증기를 통과한 것이다(명도 밴드·채도 하한·CVD 분리·정상시야 하한).
 * 눈대중으로 바꾸지 않는다 — 색을 바꾸려면 재검증이 필요하다.
 *
 * 브랜드 그린(--app-color-primary)은 버튼·활성 상태 같은 UI 크롬 전용이며 시리즈 색으로 쓰지 않는다.
 */

/** 카테고리 고정 순서. 순환하지 않으며, 슬롯을 넘기면 '기타'로 접는다. */
export const CHART_SERIES_COLORS = [
  '#2a78d6', // 1 blue
  '#eb6834', // 2 orange
  '#1baf7a', // 3 aqua
  '#eda100', // 4 yellow
  '#e87ba4', // 5 magenta
  '#008300', // 6 green
] as const

/** 순서형 램프의 가장 어두운 끝. 범위를 벗어난 인덱스는 이 색으로 떨어진다. */
const ORDINAL_DARKEST = '#104281'

/** 순서형 램프(퍼널 단계처럼 순서가 있는 축). 밝은 끝이 서피스 대비 2:1을 넘도록 250 스텝에서 시작한다. */
export const CHART_ORDINAL_COLORS = [
  '#86b6ef',
  '#5598e7',
  '#2a78d6',
  '#1c5cab',
  ORDINAL_DARKEST,
] as const

/**
 * 흰 배경 대비 3:1 미만이라 색만으로 값을 읽게 두면 안 되는 시리즈 색.
 * 이 색을 쓰는 차트는 직접 라벨이나 표 보기를 반드시 함께 제공한다.
 */
export const LOW_CONTRAST_SERIES_COLORS: readonly string[] = ['#1baf7a', '#eda100', '#e87ba4']

/**
 * 결과의 '부재'를 나타내는 중립색. 미확정(PENDING)처럼 결과가 아직 없는 구간에 쓴다.
 * 카테고리 슬롯을 쓰지 않는 이유: 슬롯 6은 녹색이라 미확정에 칠하면 합격으로 오독된다.
 */
export const CHART_NEUTRAL_COLOR = '#9ca3af'

/** 순서형 램프에서 단계 수에 맞는 색을 고른다. 단계가 램프보다 많으면 색이 반복될 수 있다. */
export const ordinalColorAt = (index: number, total: number): string => {
  const lastIndex = CHART_ORDINAL_COLORS.length - 1

  if (total <= 1) {
    return ORDINAL_DARKEST
  }

  const rawStep = Math.round((index / (total - 1)) * lastIndex)
  const step = Math.min(Math.max(rawStep, 0), lastIndex)

  return CHART_ORDINAL_COLORS[step] ?? ORDINAL_DARKEST
}
