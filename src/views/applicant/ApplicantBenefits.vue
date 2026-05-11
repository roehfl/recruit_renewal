<template>
  <section class="benefits-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">복리 후생 제도</h1>

      <!-- 탭 영역 -->
      <div class="benefit-tabs" role="tablist" aria-label="복리후생 제도 탭">
        <button
          v-for="tab in tabs"
          :id="`tab-${tab.key}`"
          :key="tab.key"
          type="button"
          class="benefit-tab"
          :class="{ active: activeTab === tab.key }"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`panel-${tab.key}`"
          @click="changeTab(tab.key)"
        >
          {{ tab.label }}
        </button>

        <div class="tab-line-fill" aria-hidden="true"></div>
      </div>

      <!-- 컨텐츠 영역 -->
      <div
        :id="`panel-${currentTab.key}`"
        class="benefit-panel"
        role="tabpanel"
        :aria-labelledby="`tab-${currentTab.key}`"
      >
        <ul class="benefit-list">
          <li v-for="(item, index) in currentTab.items" :key="index">
            {{ item }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'

type BenefitTabKey = 'benefit' | 'leave'

interface BenefitTab {
  key: BenefitTabKey
  label: string
  items: string[]
}

const activeTab = ref<BenefitTabKey>('benefit')

const tabs: [BenefitTab, BenefitTab] = [
  {
    key: 'benefit',
    label: '복리 후생 제도',
    items: [
      '경조사: 경조금(결혼 및 사망, 본인 및 배우자 부모 칠순 등) 및 화환, 조화 등 지원',
      '학자금: 유치원 및 중·고등학교, 대학교 학자금 지급',
      '의료비: 본인 및 건강보험증에 등재된 가족에게 연간 1,000만원 한도 지원',
      '치과비: 본인이 사용한 치과진료비를 5년간 100만원 한도 지원',
      '동호회비: 사내 동호회, 축구동호회, 독서동호회 등 사내 동호회 활동에 대한 경비 지원',
      '안식휴가비: 5년 근속시 100만원, 10년 근속시 200만원, 15년 이상 근속시 매 5년마다 400만원 지원',
      '주택자금대출: 직원의 전세자금 및 주택 구입자금 대출 지원',
      '선택적 복리후생제도: 개인별 부여 포인트 내에서 여가활동 등을 위해 사용한 비용 지원',
      '건강검진: 격년 주기로 종합건강검진 실시',
      '명절: 경로효친비 및 명절 선물 지원',
      '콘도이용: 전국 각지의 회사 보유 콘도 이용 지원',
      '기타 복리후생 등: 가을행사 기념품, 장기근속자 포상 등 지원',
      '교육비 지원: 직무관련 교육 이수 및 자격증 취득 시 소요 경비 지원',
      '단체상해보험 가입 지원',
    ],
  },
  {
    key: 'leave',
    label: '휴가 제도',
    items: [
      '정기휴가: 유급휴가 연 5일',
      '법정휴가: 연차휴가',
      '각종 경조휴가 및 공가',
      '안식휴가: 5년근속시 4일, 10년근속시 7일, 15년이상 근속시 매 5년마다 10일',
    ],
  },
]

const currentTab = computed<BenefitTab>(() => {
  return tabs.find((tab) => tab.key === activeTab.value) ?? tabs[0]
})

const changeTab = (key: BenefitTabKey): void => {
  activeTab.value = key
}
</script>

<style scoped>
.benefits-page {
  --theme-primary: #6f8f3d;
  --theme-primary-dark: #536d2f;
  --theme-primary-soft: #f7faf2;
  --theme-title: #183153;
  --theme-text: #202a32;
  --theme-muted: #8a8175;
  --theme-panel-shadow: rgba(31, 41, 55, 0.06);

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
   탭 영역
========================= */
.benefit-tabs {
  display: flex;
  align-items: flex-end;
  margin-top: 38px;
  overflow: visible;
  white-space: normal;
  border-bottom: none;
}

.tab-line-fill {
  flex: 1;
  min-width: 0;
  height: 58px;
  border-bottom: 2px solid var(--theme-primary);
}

.benefit-tab {
  appearance: none;
  -webkit-appearance: none;
  box-sizing: border-box;

  min-width: 250px;
  height: 58px;
  padding: 0 32px;
  margin: 0;

  display: inline-flex;
  align-items: center;
  justify-content: center;

  background: #ffffff;
  color: var(--theme-muted);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.02em;

  border: 0;
  border-bottom: 2px solid var(--theme-primary);
  border-radius: 0;

  cursor: pointer;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease;
}

.benefit-tab:hover {
  color: var(--theme-primary-dark);
}

.benefit-tab.active {
  color: var(--theme-primary-dark);
  font-weight: 700;

  border: 2px solid var(--theme-primary);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;

  background: #ffffff;
}

/* =========================
   컨텐츠 영역
========================= */
.benefit-panel {
  margin-top: 0;
  padding: 42px 52px;

  border: 2px solid var(--theme-primary);
  border-top: 0;

  border-radius: 0 0 8px 8px;

  background: linear-gradient(180deg, #ffffff 0%, var(--theme-primary-soft) 100%);
  box-shadow: 0 10px 28px var(--theme-panel-shadow);
}

.benefit-list {
  max-width: 920px;
  margin: 0;
  padding-left: 22px;
}

.benefit-list li {
  margin-bottom: 15px;
  padding-left: 4px;

  font-size: 16px;
  line-height: 1.9;
  letter-spacing: -0.025em;
  color: var(--theme-text);
}

.benefit-list li:last-child {
  margin-bottom: 0;
}

.benefit-list li::marker {
  color: var(--theme-primary-dark);
  font-size: 0.9em;
}

/* =========================
   반응형
========================= */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title {
    font-size: 30px;
  }

  .benefit-tabs {
    margin-top: 30px;
  }

  .tab-line-fill {
    display: none;
  }

  .benefit-tab {
    flex: 1;
    min-width: 0;
    height: 54px;
    padding: 0 12px;
    font-size: 14px;
  }

  .benefit-panel {
    padding: 26px 22px;
  }

  .benefit-list {
    max-width: none;
    padding-left: 18px;
  }

  .benefit-list li {
    margin-bottom: 12px;
    font-size: 14px;
    line-height: 1.75;
  }
}
</style>
