<template>
  <section class="quick-link-section">
    <div class="quick-link-grid">
      <a-card
        v-for="item in quickLinks"
        :key="item.url"
        class="quick-link-card"
        :bordered="false"
        hoverable
        @click="goPage(item.url)"
      >
        <div class="card-content">
          <div>
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-desc" v-html="item.description"></p>
          </div>

          <div class="card-bottom">
            <span class="go-text">바로가기</span>
            <RightOutlined class="go-icon" />
          </div>
        </div>
      </a-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { RightOutlined } from '@ant-design/icons-vue'

interface QuickLinkItem {
  title: string
  description: string
  url: string
}

const router = useRouter()

/*
 * TODO(route): '/applicant/company'(신영증권 소개), '/applicant/faq'(채용 FAQ),
 * '/apply'(지원서 작성/수정), '/my/result'(지원결과 조회) 는 아직 라우트가 없어
 * 클릭 시 NotFound로 빠진다. 화면이 준비되면 applicantRoutes.ts에 추가한다.
 */
const quickLinks = ref<QuickLinkItem[]>([
  {
    title: '신영증권 소개',
    description: '회사와 인재상을 먼저 만나보세요.',
    url: '/applicant/company',
  },
  {
    title: '직무 소개',
    description: '어떤 업무를 원하시나요?<br> 다양한 직무를 확인하실 수 있습니다.',
    url: '/applicant/dutyIntroduction',
  },
  {
    title: '인사제도 소개',
    description: '보상·평가, 교육제도, 복리후생을 확인하실 수 있습니다.',
    url: '/applicant/benefits',
  },
  {
    title: '지원서 작성/수정',
    description: '현재 진행중인 채용에 대한 입사지원 및 수정을 하실 수 있습니다.',
    url: '/apply',
  },
  {
    title: '지원결과 조회',
    description: '지원에 대한 결과를 확인하실 수 있습니다.',
    url: '/my/result',
  },
  {
    title: '채용 FAQ',
    description: '지원 전 자주 묻는 질문을 모았습니다.',
    url: '/applicant/faq',
  },
])

const goPage = async (url: string): Promise<void> => {
  await router.push(url)
}
</script>

<style scoped>
.quick-link-section {
  width: 100%;
  height: 100%;
}

/* 3×2. 행을 균등 분할해 좌측 채용공고 카드와 하단 라인을 맞춘다. */
.quick-link-grid {
  display: grid;
  height: 100%;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.quick-link-card {
  position: relative;
  overflow: hidden;
  min-height: 150px;
  border: 1px solid var(--app-border-soft);
  border-top: 4px solid var(--app-color-primary);
  border-radius: 8px;
  background: var(--app-content-bg-color);
  color: var(--app-text-primary);
  box-shadow: var(--app-shadow-soft-2);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.quick-link-card:hover {
  border-color: var(--app-color-primary);
  background: var(--app-color-primary);
  color: #ffffff;
  box-shadow: 0 12px 26px rgb(15 71 38 / 28%);
}

/* ant-card 내부 배경이 hover 컬러를 덮지 않도록 */
:deep(.ant-card-body) {
  height: 100%;
  padding: 22px 20px;
  background: transparent;
}

.quick-link-card:hover :deep(.ant-card-body) {
  background: transparent;
}

.card-content {
  position: relative;
  z-index: 2;
  display: flex;
  height: 100%;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  margin: 0;
  color: inherit;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.card-desc {
  margin: 10px 0 0;
  color: inherit;
  opacity: 0.82;
  font-size: 13px;
  line-height: 1.55;
  word-break: keep-all;
}

.card-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
}

.go-icon {
  font-size: 12px;
}

@media (max-width: 1100px) {
  .quick-link-grid {
    height: auto;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: none;
  }
}

@media (max-width: 768px) {
  .quick-link-grid {
    grid-template-columns: 1fr;
  }

  .quick-link-card {
    min-height: 160px;
  }

  .card-content {
    height: auto;
    min-height: 112px;
  }
}
</style>
