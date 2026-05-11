<template>
  <main class="applicant-main">
    <section class="main-visual">
      <div class="visual-text">
        <p>Be Different, Be Bold</p>
        <h1>신영증권 채용</h1>
        <span>RECRUIT</span>
      </div>
    </section>

    <section class="main-content">
      <div class="left-area">
        <ApplicantRecruitList />
      </div>

      <div class="right-area">
        <ApplicantNoticeList :max-count="4" />
        <ApplicantQuickLinkCards />
        <!-- <ApplicantNoticeList /> -->
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import ApplicantRecruitList from '@/views/applicant/ApplicantRecruitList.vue'
import ApplicantQuickLinkCards from '@/views/applicant/ApplicantQuickLinkCards.vue'
import ApplicantNoticeList from '@/views/applicant/ApplicantNoticeList.vue'
</script>

<style scoped>
.applicant-main {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}

.main-visual {
  height: 180px;
  margin-bottom: 24px;
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  background:
    radial-gradient(circle at 85% 30%, rgba(47, 111, 85, 0.14), transparent 28%),
    linear-gradient(135deg, #ffffff 0%, #f7faf8 55%, #eef6f1 100%);
}

.visual-text {
  padding: 38px 48px;
}

.visual-text p {
  margin: 0 0 10px;
  color: #2f6f55;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.visual-text h1 {
  margin: 0;
  margin-left: 40px;
  color: #20242a;
  font-size: 42px;
  font-weight: 800;
}

.visual-text span {
  display: block;
  margin-top: 12px;
  margin-left: 40px;
  color: #2f6f55;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.7em;
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
