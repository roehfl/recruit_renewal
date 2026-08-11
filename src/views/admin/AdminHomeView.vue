<script setup lang="ts">
/*
 * 관리자 프레임의 기본 화면. 개별 관리자 화면은 후속 슬라이스에서 이 자리에 라우팅된다.
 */
</script>

<template>
  <div class="admin-page">
    <header class="page-header">
      <h2 class="page-title">대시보드</h2>
      <p class="page-description">신영증권 채용 관리자</p>
    </header>

    <section class="page-body">
      <p class="page-placeholder">좌측 메뉴에서 관리할 항목을 선택하세요.</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 800;
  color: var(--app-text-primary);
  letter-spacing: -0.02em;
}

.page-description {
  margin: 0;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}

.page-body {
  padding: 48px 20px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
}

.page-placeholder {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
