<template>
  <section class="quick-link-section">
    <!-- <div class="section-header">
      <h2 class="section-title">바로가기</h2>
      <p class="section-desc">지원자에게 필요한 주요 메뉴를 바로 확인하세요.</p>
    </div> -->

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
            <!-- <p class="card-desc">{{ item.description }}</p> -->
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

const quickLinks = ref<QuickLinkItem[]>([
  {
    title: '입사지원/수정',
    description: '현재 진행중인 채용에 대한 입사지원 및 수정을 하실 수 있습니다.',
    url: '/apply',
  },
  {
    title: '합격자 조회',
    description: '지원에 대한 결과를 확인하실 수 있습니다.',
    url: '/my/result',
  },
  {
    title: '직무소개',
    description: '어떤 업무를 원하시나요?<br> 다양한 직무를 확인하실 수 있습니다.',
    url: '/applicant/dutyIntroduction',
  },
])

const goPage = async (url: string): Promise<void> => {
  await router.push(url)
}
</script>

<style scoped>
.quick-link-section {
  width: 100%;
  /* border: 1px solid var(--app-divider-color);
  border-radius: 10px;
  background: var(--app-bg-surface);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
  padding: 24px; */
}

.section-header {
  margin-bottom: 14px;
}

.section-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.section-desc {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.45;
}

.quick-link-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.quick-link-card {
  position: relative;
  overflow: hidden;
  min-height: 150px;
  border: 1px solid var(--app-border-soft);
  border-top: 4px solid #2f6f55;
  border-radius: 8px;
  background: var(--app-content-bg-color);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.045);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.quick-link-card:hover {
  transform: translateY(-4px);
  border-color: var(--app-color-warning);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}

:deep(.ant-card-body) {
  height: 100%;
  padding: 22px 20px;
}

.card-content {
  position: relative;
  z-index: 2;
  display: flex;
  min-height: 142px;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
}

.card-desc {
  margin: 12px 0 0;
  color: var(--app-text-primary);
  font-size: 14px;
  line-height: 1.55;
  word-break: keep-all;
}

.card-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-primary-color);
  font-size: 14px;
  font-weight: 600;
}

.go-icon {
  font-size: 12px;
}


@media (max-width: 1100px) {
  .quick-link-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
    min-height: 112px;
  }

  .section-title {
    font-size: 20px;
  }

  .card-title {
    font-size: 18px;
  }
}
</style>
