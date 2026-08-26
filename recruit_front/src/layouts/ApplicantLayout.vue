<script setup lang="ts">
import { RouterView } from 'vue-router'
import ApplicantHeader from './ApplicantHeader.vue'
import ApplicantFooter from './ApplicantFooter.vue'
</script>

<template>
  <div class="applicant-layout">
    <div class="applicant-frame">
      <ApplicantHeader />

      <main class="applicant-content">
        <RouterView />
      </main>

      <ApplicantFooter />
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 프레임 바깥 영역 */
.applicant-layout {
  min-height: 100vh;
  background: var(--app-bg-page);
}

/* 헤더 + 본문 + 푸터를 감싸는 프레임. 윤곽선이 세 영역을 모두 두른다. */
.applicant-frame {
  display: flex;
  flex-direction: column;
  width: var(--app-frame-width);
  min-height: 100vh;
  margin: 0 auto;
  border: 1px solid var(--app-border-color);
  background: var(--app-content-bg-color);
}

/*
 * 프레임(.applicant-frame)이 이미 --app-frame-width(85%)를 적용하므로,
 * 그 안쪽 화면들이 같은 토큰으로 한 번 더 85%를 먹지 않도록 100%로 덮는다.
 * (지원자 화면들은 공통적으로 max-width: var(--app-frame-width)를 쓴다)
 */
.applicant-content {
  --app-frame-width: 100%;

  flex: 1 1 auto;
}
</style>
