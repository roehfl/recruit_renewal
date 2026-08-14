<script setup lang="ts">
/*
 * 대시보드 카드 껍데기. 제목/부제/우측 액션(주로 "표 보기" 토글)과 본문 슬롯만 제공한다.
 * 카드 높이는 내용에 맞춰 늘어난다 — 높이를 고정하면 x축 라벨이 잘려 카드 안에 세로 스크롤이 생긴다.
 */
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <section class="dashboard-card">
    <header class="card-head">
      <h3 class="card-title">{{ title }}</h3>
      <span v-if="subtitle" class="card-subtitle">{{ subtitle }}</span>
      <span class="card-action">
        <slot name="action" />
      </span>
    </header>

    <div class="card-body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.dashboard-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px 18px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-shadow-soft);
}

.card-head {
  flex: none;
  margin-bottom: 14px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.card-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.card-subtitle {
  min-width: 0;
  font-size: 11px;
  color: var(--app-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card-action {
  margin-left: auto;
  flex: none;
}

.card-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
</style>
