<template>
  <ApplicantInfoTabPanel
    title="인사 제도"
    subtitle="보상 및 평가, 교육, 복리후생 제도를 안내합니다."
    large-title
    :tabs="tabs"
    :initial-tab-key="initialTabKey"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

/* 보상 및 평가 */
const reward: CardItem[] = [
  { label: '연봉제 시행', desc: '직원 개개인의 역량과 성과에 근거한 연봉제 시행' },
  { label: '성과급 지급', desc: '직원 개개인의 종합평가 결과에 근거한 성과급 지급' },
]

const review: CardItem[] = [
  {
    label: '평가그룹별 평가 시행',
    desc: 'Assistant: 신입사원에 한해 입사 후 일정기간 Career path 탐색 기회 부여 · Manager: 역량 및 성과에 따른 종합평가',
  },
  {
    label: '평가방법',
    desc: '역량평가: 평가그룹별 assign 된 역량 평가 · 성과평가: 설정 목표 대비 달성도 평가 · 종합평가: 역량·성과를 종합해 승진·전보·연봉을 합리적으로 결정',
  },
]

/* 교육제도 */
const internal: CardItem[] = [
  { label: '직무교육', desc: '자산관리전문가 · 고객업무전문가 · 실무교육' },
  {
    label: '직급교육',
    desc: '신입사원 입문교육 · 수시입사자 안내 · Follow-up 과정 · 승진 자격과정 · 리더십과정',
  },
  { label: '자격관리교육', desc: '자격시험 대비과정 · 투자권유자문인력 투자자보호' },
  { label: 'ADVANCED 교육', desc: '고급관계관리(ARM) · 고급정보기술(AIT)' },
]

const external: CardItem[] = [
  { label: '금융투자교육원 주관 교육', desc: '전문교육 · 집합교육 · 온라인교육' },
  {
    label: '기타 외부기관 주관 교육',
    desc: '영업 · 운용 · IB · 리서치 · IT · 지원 · 준법/리스크/정보보호 · 공통',
  },
  { label: '교육 신청절차', desc: '금융투자교육원 교육 신청 · 기타 외부기관 주관 교육 신청' },
]

/* 복리후생 */
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
  { label: '안식휴가', desc: '5년 4일, 10년 7일, 15년 이상 매 5년 10일' },
]

const pick = (...idx: number[]): CardItem[] => idx.map((i) => benefits[i]!)

const tabs: PanelTab[] = [
  {
    key: 'reward',
    label: '보상 및 평가',
    chips: [
      { key: 'all', label: '전체', items: [...reward, ...review] },
      { key: 'reward', label: '보상제도', items: reward },
      { key: 'review', label: '평가제도', items: review },
    ],
  },
  {
    key: 'training',
    label: '교육제도',
    chips: [
      { key: 'all', label: '전체', items: [...internal, ...external] },
      { key: 'internal', label: '사내교육', items: internal },
      { key: 'external', label: '사외교육', items: external },
    ],
  },
  {
    key: 'benefit',
    label: '복리후생',
    chips: [
      { key: 'all', label: '전체', items: [...benefits, ...leaves] },
      { key: 'econ', label: '경제적 지원', items: pick(0, 6, 7, 9) },
      { key: 'health', label: '건강과 의료', items: pick(2, 3, 8, 13) },
      { key: 'leisure', label: '여가와 문화', items: pick(4, 10, 5, 11) },
      { key: 'growth', label: '성장과 교육', items: pick(1, 12) },
      { key: 'leave', label: '휴가', items: leaves },
    ],
  },
]

const route = useRoute()

// /applicant/benefits?tab=reward|training|benefit 으로 진입 탭을 지정한다.
const initialTabKey = computed<string>(() => {
  const tab = route.query.tab
  return typeof tab === 'string' ? tab : ''
})
</script>
