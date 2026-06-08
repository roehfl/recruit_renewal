<template>
  <section class="recruitProcedure-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">채용절차</h1>

      <div aria-label="채용절차">
        <a-descriptions bordered :column="1" >
            <a-descriptions-item label="1. 입사지원">
                정기/수시채용 : 채용공고를 통한 일정 안내 및 온라인 지원
            </a-descriptions-item
            ><a-descriptions-item label="2. 서류전형">
                입사지원서를 기본으로 지원자의 기본역량 평가
            </a-descriptions-item
            ><a-descriptions-item label="3. 인성/역량 인터뷰">
                부서장급 평가자로 구성된 실무진 면접
            </a-descriptions-item
            ><a-descriptions-item label="4. 임원 최종 인터뷰">
                임원진 면접을 통한 합격자 결정
            </a-descriptions-item
            ><a-descriptions-item label="5. 최종 결과 통보">
                채용공고란을 통해 합격 여부 확인
            </a-descriptions-item
            >
        </a-descriptions>
      </div>

    </div>
  </section>
</template>

<script setup lang="ts">
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'

</script>

<style scoped>
.recruitProcedure-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 88px;
}

.page-title {
  margin-bottom: 38px;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.sample-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 60px;
}

:deep(.ant-descriptions-item-label) {
  font-size: 16px;
  font-weight: 500;
}

:deep(.ant-descriptions-item-content) {
  font-size: 16px;
}


/* =========================
   반응형
========================= */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title {
    font-size: 30px;
  }

  .benefit-tabs {
    margin-top: 30px;
  }
}
</style>
