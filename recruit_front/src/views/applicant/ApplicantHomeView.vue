<template>
  <main class="applicant-main">
    <MainBannerSwitcher class="main-visual" />

    <section class="main-content">
      <div class="left-area">
        <ApplicantRecruitList />
      </div>

      <div class="right-area">
        <ApplicantQuickLinkCards />
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import MainBannerSwitcher from '@/views/applicant/banners/MainBannerSwitcher.vue'
import ApplicantRecruitList from '@/views/applicant/ApplicantRecruitList.vue'
import ApplicantQuickLinkCards from '@/views/applicant/ApplicantQuickLinkCards.vue'
</script>

<style scoped>
.applicant-main {
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 32px var(--app-frame-padding-x) 60px;
}

.main-visual {
  margin-bottom: 24px;
}

.main-content {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 32px;
  align-items: start;
}

.left-area {
  min-width: 0;
}

.right-area {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

@media (max-width: 960px) {
  .main-content {
    grid-template-columns: 1fr;
  }
}
</style>
