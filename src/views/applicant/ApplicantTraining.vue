<template>
  <section class="training-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">교육제도</h1>

      <!-- 탭 영역 -->
      <div class="tab-buttons" role="tablist" aria-label="교육제도 탭">
        <button
          v-for="tab in tabs"
          :id="`tab-${tab.key}`"
          :key="tab.key"
          type="button"
          class="tab-button"
          :class="{ active: activeTab === tab.key }"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`panel-${tab.key}`"
          @click="changeTab(tab.key)"
        >
          {{ tab.label }}
        </button>

        <div class="tab-button-line-fill" aria-hidden="true"></div>
      </div>

      <!-- 컨텐츠 영역 -->
      <div
        :id="`panel-${currentTab.key}`"
        class="tab-panel"
        role="tabpanel"
        :aria-labelledby="`tab-${currentTab.key}`"
      >
        <ul class="training-list tab-list">
          <li v-for="(item, index) in currentTab.items" :key="index">
            {{ item.content }}
              <ul v-if="item.children?.length">
                <li v-for="(child, childIndex) in item.children" :key="childIndex">
                  {{ child }}
                </li>
              </ul>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'

type TrainingTabKey = 'internal' | 'external' | 'mandatory'

interface TrainingTab {
  key: TrainingTabKey
  label: string
  items: TrainingItem[]
}

interface TrainingItem {
  content: string
  children?: string[]
}

const activeTab = ref<TrainingTabKey>('internal')

const tabs: [TrainingTab, TrainingTab, TrainingTab] = [
  {
    key: 'internal',
    label: '사내교육',
    items: [
      {
        content: '직무교육', 
        children: [ '자산관리전문가', '고객업무전문가', '실무교육' ]
        // children: [ '자산관리전문가, 고객업무전문가, 실무교육' ]
      }, 
      {
        content: '직급교육', 
        children: [ '신입사원 입문교육', '수시입사자를 위한 안내', 'Follow-up과정', '승진 자격과정', '리더십과정' ]
        // children: [ '신입사원 입문교육, 수시입사자를 위한 안내, Follow-up과정, 승진 자격과정, 리더십과정' ]
      }, 
      {
        content: '자격관리교육', 
        children: [ '자격시험 대비과정', '투자권유자문인력 투자자보호' ]
        // children: [ '자격시험 대비과정, 투자권유자문인력 투자자보호' ]
      }, 
      {
        content: 'ADVANCED교육', 
        children: [ '고급관계관리(ARM)', '고급정보기술(AIT)' ]
        // children: [ '고급관계관리(ARM), 고급정보기술(AIT)' ]
      }, 
    ],
  },
  {
    key: 'external',
    label: '사외교육',
    items: [
      {
        content: '금융투자교육원 주관 교육', 
        children: [ '전문교육', '집합교육', '온라인교육' ]
        // children: [ '전문교육, 집합교육, 온라인교육' ]
      }, 
      {
        content: '기타 외부기관 주관 교육', 
        children: [ '영업', '운용', 'IB', '리서치', 'IT', '지원','준법/리스크/정보보호', '공통' ]
        // children: [ '영업, 운용, IB, 리서치, IT, 지원,준법/리스크/정보보호, 공통' ]
      }, 
      {
        content: '교육 신청절차', 
        children: [ '금융투자교육원 교육 신청','기타 외부기관 주관 교육 신청' ]
        // children: [ '금융투자교육원 교육 신청,기타 외부기관 주관 교육 신청' ]
      }, 
    ],
  },
    {
    key: 'mandatory',
    label: '법정의무교육',
    items: [
      {
        content: '직장 내 성희롱 예방교육', 
        children: [ '남녀고용평등과 일·가정 양립 지원에 관한 법률' ]
      }, 
      {
        content: '금융정보보호교육', 
        children: [ '전자금융감독규정 제 19조 2항' ]
      }, 
      {
        content: '금융투자전문인력 보수교육', 
        children: [ '금융투자전문인력과 자격시험에 관한 규정 제5-2조 4항' ]
      }, 
    ],
  },
]

const currentTab = computed<TrainingTab>(() => {
  return tabs.find((tab) => tab.key === activeTab.value) ?? tabs[0]
})

const changeTab = (key: TrainingTabKey): void => {
  activeTab.value = key
}
</script>

<style scoped>
.training-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 88px;
}

.page-title {
  margin: 0;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

/* =========================
   탭 컨텐츠 영역
========================= */
.training-list ul {
  margin-top: 5px;
}

.training-list li {
  margin-bottom: 10px;
  padding-left: 4px;

  font-size: 16px;
  line-height: 1.9;
  letter-spacing: -0.025em;
  color: var(--tap-text);
}

</style>
