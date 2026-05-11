<script setup lang="ts">
import { RouterView } from 'vue-router'
import ApplicantHeader from './ApplicantHeader.vue'
import ApplicantFooter from './ApplicantFooter.vue'
</script>

<template>
  <div class="applicant-layout">
    <ApplicantHeader />

    <main class="applicant-content">
      <RouterView />
    </main>

    <ApplicantFooter />
  </div>
</template>

<style scoped lang="scss">
.applicant-layout {
  min-height: 100vh;
  background: var(--app-content-bg-color);
}

.applicant-content {
  min-height: calc(100vh - 130px);
}
</style>
