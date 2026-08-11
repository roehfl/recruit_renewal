<script setup lang="ts">
import { RouterView } from 'vue-router'
import AdminSidebar from './AdminSidebar.vue'
</script>

<template>
  <div class="admin-layout">
    <AdminSidebar />

    <!-- 좌측 사이드바를 제외한 우측 영역이 라우팅된 관리자 화면의 출력 공간이다. -->
    <main class="admin-content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  height: 100vh;
  display: flex;
  overflow: hidden;
  background: var(--app-bg-page);
}

.admin-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 22px 24px;
  background: var(--app-bg-page);
}
</style>
