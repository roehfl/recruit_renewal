<template>
  <ApplicantInfoTabPanel
    title="복리 후생 제도"
    subtitle="임직원의 안정적인 생활과 성장을 위한 다양한 지원 제도를 안내합니다."
    large-title
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

// 인덱스 0~13 — 카테고리 칩이 인덱스로 그룹을 참조한다(중복 desc 방지).
const benefits: CardItem[] = [
  { label: '경조사', desc: '경조금(결혼·사망, 본인 및 배우자 부모 칠순 등)과 화환·조화 지원' }, // 0
  { label: '학자금', desc: '유치원·중·고등학교, 대학교 학자금 지급' }, // 1
  { label: '의료비', desc: '본인 및 건강보험증 등재 가족에게 연간 1,000만원 한도 지원' }, // 2
  { label: '치과비', desc: '본인이 사용한 치과진료비를 5년간 100만원 한도 지원' }, // 3
  { label: '동호회비', desc: '축구·독서 등 사내 동호회 활동 경비 지원' }, // 4
  { label: '안식휴가비', desc: '5년 100만원, 10년 200만원, 15년 이상 매 5년 400만원' }, // 5
  { label: '주택자금대출', desc: '직원 전세자금 및 주택 구입자금 대출 지원' }, // 6
  { label: '선택적 복리후생', desc: '개인별 부여 포인트 내에서 여가활동 등 비용 지원' }, // 7
  { label: '건강검진', desc: '격년 주기로 종합건강검진 실시' }, // 8
  { label: '명절', desc: '경로효친비 및 명절 선물 지원' }, // 9
  { label: '콘도 이용', desc: '전국 각지의 회사 보유 콘도 이용 지원' }, // 10
  { label: '기타 복리후생', desc: '가을행사 기념품, 장기근속자 포상 등 지원' }, // 11
  { label: '교육비 지원', desc: '직무관련 교육 이수 및 자격증 취득 경비 지원' }, // 12
  { label: '단체상해보험', desc: '임직원 단체상해보험 가입 지원' }, // 13
]

const leaves: CardItem[] = [
  { label: '정기휴가', desc: '유급휴가 연 5일' },
  { label: '법정휴가', desc: '근로기준법에 따른 연차휴가' },
  { label: '경조휴가·공가', desc: '각종 경조사 휴가 및 공가' },
  { label: '안식휴가', desc: '5년 4일, 10년 7일, 15년 이상 매 5년 10일' },
]

const pick = (...idx: number[]): CardItem[] => idx.map((i) => benefits[i]!)

const tabs: PanelTab[] = [
  {
    key: 'benefit',
    label: '복리 후생 제도',
    chips: [
      { key: 'all', label: '전체', items: benefits },
      { key: 'econ', label: '경제적 지원', items: pick(0, 6, 7, 9) },
      { key: 'health', label: '건강과 의료', items: pick(2, 3, 8, 13) },
      { key: 'leisure', label: '여가와 문화', items: pick(4, 10, 5, 11) },
      { key: 'growth', label: '성장과 교육', items: pick(1, 12) },
    ],
  },
  {
    key: 'leave',
    label: '휴가 제도',
    chips: [{ key: 'all', label: '전체', items: leaves }],
  },
]
</script>
