<template>
  <section class="hrRule-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">보상 및 평가</h1>

      <!-- 탭 영역 -->
      <div class="tab-buttons" role="tablist" aria-label="보상 및 평가 탭">
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
        <ul class="hrRule-list tab-list">
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

type HrRuleTabKey = 'reward' | 'review'

interface HrRuleTab {
  key: HrRuleTabKey
  label: string
  items: HrRuleItem[]
}

interface HrRuleItem {
  content: string
  children?: string[]
}

const activeTab = ref<HrRuleTabKey>('reward')

const tabs: [HrRuleTab, HrRuleTab] = [
  {
    key: 'reward',
    label: '보상제도',
    items: [
      {
        content: '직원 개개인의 역량과 성과에 근거한 연봉제 시행', 
        children: []
      },
      {
        content: '직원 개개인의 종합평가 결과에 근거한 성과급 지급', 
        children: []
      },
    ],
  },
  {
    key: 'review',
    label: '평가제도',
    items: [
      {
        content: '평가그룹별 평가 시행', 
        children: [
          'Assistant : 신입사원에 한해 입사 후 일정기간 Career path 탐색 기회 부여',
          'Manager : 역량 및 성과에 따른 종합평가',
        ]
      }, 
      {
        content: '평가방법', 
        children: [
          '역량평가 : 평가그룹별로 적절히 assign 된 역량에 대하여 평가 실시',
          '성과평가 : 최초에 설정한 목표 대비 달성도에 대하여 평가 실시',
          '종합평가 : 역량평가와 성과평가를 종합하여 최종 평가 실시,종합평가를 근거로 승진/전보/연봉 등 합리적 결정',
        ]
      }, 
    ],
  },
]

const currentTab = computed<HrRuleTab>(() => {
  return tabs.find((tab) => tab.key === activeTab.value) ?? tabs[0]
})

const changeTab = (key: HrRuleTabKey): void => {
  activeTab.value = key
}
</script>

<style scoped>
.hrRule-page {
  width: 100%;
  background: #ffffff;
  color: var(--theme-text);
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
  color: var(--theme-title);
}

/* =========================
   탭 컨텐츠 영역
========================= */

.hrRule-list ul {
  margin-top: 10px;
}

</style>
