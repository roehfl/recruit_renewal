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
    </div>

    <ApplicantFooter />
  </div>
</template>

<style scoped lang="scss">
/* 프레임 바깥 영역 */
.applicant-layout {
  min-height: 100vh;
  background: var(--app-bg-page);
}

/*
 * 헤더 + 본문을 감싸는 프레임. 하단 테두리는 고정 푸터의 border-top이 이어받으므로 생략한다.
 * padding-bottom은 고정 푸터에 콘텐츠가 가리지 않도록 확보한 공간이다.
 */
.applicant-frame {
  display: flex;
  flex-direction: column;
  width: var(--app-frame-width);
  min-height: 100vh;
  margin: 0 auto;
  padding-bottom: var(--app-footer-height);
  border: 1px solid var(--app-border-color);
  border-bottom: 0;
  background: var(--app-content-bg-color);
}

.applicant-content {
  flex: 1 1 auto;
}
</style>
