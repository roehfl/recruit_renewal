<template>
  <section class="recruitProcedure-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title">채용절차</h1>

      <div class="procedure-steps" aria-label="채용절차">
        <div class="step-row step-row--tint">
          <span class="step-num">01</span>
          <span class="step-divider" aria-hidden="true"></span>
          <div class="step-body">
            <div class="step-name">입사지원</div>
            <p class="step-desc">정기/수시채용 : 채용공고를 통한 일정 안내 및 온라인 지원</p>
          </div>
        </div>

        <div class="step-row step-row--plain">
          <span class="step-num">02</span>
          <span class="step-divider" aria-hidden="true"></span>
          <div class="step-body">
            <div class="step-name">서류전형</div>
            <p class="step-desc">입사지원서를 기본으로 지원자의 기본역량 평가</p>
          </div>
        </div>

        <div class="step-row step-row--tint">
          <span class="step-num">03</span>
          <span class="step-divider" aria-hidden="true"></span>
          <div class="step-body">
            <div class="step-name">인성/역량 인터뷰</div>
            <p class="step-desc">부서장급 평가자로 구성된 실무진 면접</p>
          </div>
        </div>

        <div class="step-row step-row--plain">
          <span class="step-num">04</span>
          <span class="step-divider" aria-hidden="true"></span>
          <div class="step-body">
            <div class="step-name">임원 최종 인터뷰</div>
            <p class="step-desc">임원진 면접을 통한 합격자 결정</p>
          </div>
        </div>

        <div class="step-row step-row--final">
          <span class="step-num">05</span>
          <span class="step-divider" aria-hidden="true"></span>
          <div class="step-body">
            <div class="step-name">최종 결과 통보</div>
            <p class="step-desc">채용공고란을 통해 합격 여부 확인</p>
          </div>
        </div>
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

.procedure-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 24px 30px;
  border-radius: 14px;
}

.step-row--tint {
  background: #f8faf6;
}

.step-row--plain {
  background: #ffffff;
  border: 1px solid #eef1f4;
}

.step-row--final {
  background: #0f4726;
}

.step-num {
  flex: 0 0 auto;
  width: 78px;
  text-align: center;
  font-size: 58px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.04em;
  color: #2f6f55;
}

.step-row--final .step-num {
  color: #7fb79a;
}

.step-divider {
  flex: 0 0 auto;
  width: 1px;
  align-self: stretch;
  background: #e1e9dd;
}

.step-row--plain .step-divider {
  background: #eef1f4;
}

.step-row--final .step-divider {
  background: rgba(255, 255, 255, 0.18);
}

.step-body {
  flex: 1;
}

.step-name {
  font-size: 21px;
  font-weight: 700;
  color: #1f2937;
}

.step-desc {
  margin: 8px 0 0;
  font-size: 15.5px;
  line-height: 1.7;
  color: #6b7280;
  word-break: keep-all;
}

.step-row--final .step-name {
  color: #ffffff;
}

.step-row--final .step-desc {
  color: #cfe3d7;
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

  .step-row {
    gap: 18px;
    padding: 20px 18px;
  }

  .step-num {
    width: 54px;
    font-size: 42px;
  }

  .step-name {
    font-size: 18px;
  }
}
</style>
