<template>
  <section class="info-tab-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title" :class="{ lg: largeTitle }">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>

      <!-- 탭 -->
      <div class="tab-row" role="tablist">
        <button
          v-for="tab in tabs"
          :id="`tab-${tab.key}`"
          :key="tab.key"
          type="button"
          class="tab-btn"
          :class="{ active: tab.key === activeTabKey }"
          role="tab"
          :aria-selected="tab.key === activeTabKey"
          :aria-controls="`panel-${tab.key}`"
          @click="selectTab(tab.key)"
        >
          {{ tab.label }}
        </button>
        <div class="tab-fill" aria-hidden="true"></div>
      </div>

      <!-- 패널 -->
      <div
        :id="`panel-${activeTabKey}`"
        class="panel"
        role="tabpanel"
        :aria-labelledby="`tab-${activeTabKey}`"
      >
        <div class="panel-inner">
          <!-- 칩 -->
          <div class="chip-row">
            <button
              v-for="chip in activeTab.chips"
              :key="chip.key"
              type="button"
              class="chip"
              :class="{ active: chip.key === activeChipKey }"
              @click="selectChip(chip.key)"
            >
              {{ chip.label }}
              <span class="chip-count">{{ chip.items.length }}</span>
            </button>
          </div>

          <!-- 카드 리스트 -->
          <div class="card-list" :class="{ 'two-col': activeItems.length > 7 }">
            <div v-for="(item, i) in activeItems" :key="i" class="card">
              <div class="card-bar" aria-hidden="true"></div>
              <div class="card-body">
                <div class="card-label">{{ item.label }}</div>
                <div v-if="item.desc" class="card-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

const props = defineProps<{
  title: string
  subtitle?: string
  largeTitle?: boolean
  tabs: PanelTab[]
}>()

const activeTabKey = ref<string>(props.tabs[0]?.key ?? '')

const activeTab = computed<PanelTab>(
  () => (props.tabs.find((t) => t.key === activeTabKey.value) ?? props.tabs[0])!,
)

const activeChipKey = ref<string>(props.tabs[0]?.chips[0]?.key ?? '')

const activeChip = computed(
  () =>
    activeTab.value.chips.find((c) => c.key === activeChipKey.value) ?? activeTab.value.chips[0],
)

const activeItems = computed<CardItem[]>(() => activeChip.value?.items ?? [])

const selectTab = (key: string): void => {
  activeTabKey.value = key
  const next = props.tabs.find((t) => t.key === key)
  activeChipKey.value = next?.chips[0]?.key ?? '' // 탭 변경 시 첫 칩으로 리셋
}

const selectChip = (key: string): void => {
  activeChipKey.value = key
}
</script>

<style scoped>
.info-tab-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 80px;
}

.page-title {
  margin: 18px 0 0;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.page-title.lg {
  font-size: 40px;
}

.page-subtitle {
  margin: 9px 0 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--app-text-muted);
  letter-spacing: -0.02em;
}

/* 탭 */
.tab-row {
  display: flex;
  align-items: flex-end;
  margin-top: 34px;
}

.tab-btn {
  appearance: none;
  -webkit-appearance: none;
  box-sizing: border-box;
  min-width: 230px;
  height: 58px;
  padding: 0 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  letter-spacing: -0.02em;
  color: var(--app-text-muted);
  font-weight: 500;
  border: 0;
  border-bottom: 2px solid var(--app-color-primary-hover);
  border-radius: 8px 8px 0 0;
  background: var(--tap-muted-soft);
  cursor: pointer;
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}

.tab-btn:hover {
  color: var(--app-color-primary);
}

.tab-btn.active {
  color: var(--app-color-primary-hover);
  font-weight: 700;
  border: 2px solid var(--app-color-primary-hover);
  border-bottom: 0;
  background: #ffffff;
}

.tab-fill {
  flex: 1;
  min-width: 0;
  height: 58px;
  border-bottom: 2px solid var(--app-color-primary-hover);
}

/* 패널 */
.panel {
  border: 2px solid var(--app-color-primary-hover);
  border-top: 0;
  border-radius: 0 0 8px 8px;
  background: #ffffff;
  box-shadow: 0 10px 28px var(--tap-panel-shadow);
}

.panel-inner {
  padding: 34px 44px 42px;
}

/* 칩 */
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 26px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 15px;
  border-radius: 20px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--tap-chip-text);
  background: #ffffff;
  border: 1px solid var(--tap-chip-border);
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip.active {
  background: var(--app-color-primary);
  color: #ffffff;
  border-color: var(--app-color-primary);
}

.chip-count {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--tap-chip-count);
}

.chip.active .chip-count {
  color: rgba(255, 255, 255, 0.7);
}

/* 카드 리스트 */
.card-list {
  column-count: 1;
  column-gap: 30px;
}

.card-list.two-col {
  column-count: 2;
}

.card {
  display: flex;
  gap: 13px;
  break-inside: avoid;
  padding: 14px 4px;
  border-bottom: 1px solid var(--tap-card-divider);
}

.card-bar {
  flex-shrink: 0;
  width: 3px;
  align-self: stretch;
  background: var(--tap-accent-bar);
  border-radius: 2px;
}

.card-label {
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text-primary);
  letter-spacing: -0.01em;
}

.card-desc {
  margin-top: 5px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);
  letter-spacing: -0.02em;
  word-break: keep-all;
  text-wrap: pretty;
}

/* 반응형 */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title,
  .page-title.lg {
    font-size: 30px;
  }

  .tab-row {
    margin-top: 28px;
  }

  .tab-fill {
    display: none;
  }

  .tab-btn {
    flex: 1;
    min-width: 0;
    height: 52px;
    padding: 0 12px;
    font-size: 14px;
  }

  .panel-inner {
    padding: 24px 18px 30px;
  }

  .card-list.two-col {
    column-count: 1;
  }
}
</style>
