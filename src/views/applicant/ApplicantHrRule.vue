<template>
  <ApplicantInfoTabPanel
    title="보상 및 평가"
    subtitle="역량과 성과에 기반한 보상 체계와 공정한 평가 제도를 안내합니다."
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

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

const tabs: PanelTab[] = [
  { key: 'reward', label: '보상제도', chips: [{ key: 'all', label: '전체', items: reward }] },
  { key: 'review', label: '평가제도', chips: [{ key: 'all', label: '전체', items: review }] },
]
</script>
