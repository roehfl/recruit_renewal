<template>
  <ApplicantInfoTabPanel
    title="교육 제도"
    subtitle="임직원의 전문성과 성장을 지원하는 사내·외 교육 과정을 안내합니다."
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

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

const mandatory: CardItem[] = [
  { label: '직장 내 성희롱 예방교육', desc: '근거: 남녀고용평등과 일·가정 양립 지원에 관한 법률' },
  { label: '금융정보보호교육', desc: '근거: 전자금융감독규정 제19조 2항' },
  {
    label: '금융투자전문인력 보수교육',
    desc: '근거: 금융투자전문인력과 자격시험에 관한 규정 제5-2조 4항',
  },
]

const tabs: PanelTab[] = [
  { key: 'internal', label: '사내교육', chips: [{ key: 'all', label: '전체', items: internal }] },
  { key: 'external', label: '사외교육', chips: [{ key: 'all', label: '전체', items: external }] },
  { key: 'mandatory', label: '법정의무교육', chips: [{ key: 'all', label: '전체', items: mandatory }] },
]
</script>
