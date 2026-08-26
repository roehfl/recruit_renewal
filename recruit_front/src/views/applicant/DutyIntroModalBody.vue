<template>
  <div class="dm-modal">
    <!-- header -->
    <div class="dm-header">
      <div class="dm-eyebrow">
        <span class="dm-dot"></span>
        <span class="dm-eyebrow-text">{{ job.eyebrow }}</span>
      </div>
      <h2 class="dm-title">{{ job.title }}</h2>
      <button class="dm-close" type="button" aria-label="닫기" @click="emit('close')">✕</button>
    </div>

    <!-- body -->
    <div class="dm-scroll dm-body">
      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">1</span>
          <h3 class="dm-section-title">직무소개</h3>
        </div>
        <p class="dm-text dm-indent">{{ job.intro }}</p>
      </section>

      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">2</span>
          <h3 class="dm-section-title">필요역량</h3>
        </div>
        <div class="dm-indent">
          <div class="dm-pills">
            <span class="dm-pill">전공 <span class="dm-pill-val">{{ job.major }}</span></span>
            <span class="dm-pill">학위 <span class="dm-pill-val">{{ job.degree }}</span></span>
          </div>
          <div class="dm-skills">
            <div v-for="(s, i) in job.skills" :key="i" class="dm-skill">
              <span class="dm-diamond"></span>
              <p class="dm-text"><b class="dm-skill-lead">{{ s.lead }}</b> {{ s.body }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">3</span>
          <h3 class="dm-section-title">커리어패스</h3>
        </div>
        <p class="dm-text dm-indent">{{ job.career }}</p>
      </section>

      <div class="dm-highlight">
        <h3 class="dm-highlight-title">신영증권만의 차별점</h3>
        <p class="dm-text">{{ job.diff }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DutyItem } from '@/types/duty'

defineProps<{ job: DutyItem }>()
const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<style scoped>
.dm-modal {
  font-family: 'Noto Sans KR', 'Noto Sans CJK KR', 'Apple SD Gothic Neo', sans-serif;
  color: #1f2937;
}

.dm-header {
  position: relative;
  padding: 32px 40px 24px;
  border-bottom: 1px solid #eef1ee;
}

.dm-eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.dm-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #2f6f55;
}

.dm-eyebrow-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #2f6f55;
  text-transform: uppercase;
}

.dm-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
}

.dm-close {
  position: absolute;
  top: 28px;
  right: 32px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: #f5f7fa;
  color: #6b7280;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.dm-close:hover {
  background: #eaf8f3;
  color: #0f4726;
}

.dm-body {
  max-height: 560px;
  overflow-y: auto;
  padding: 28px 40px 36px;
}

.dm-section {
  margin-bottom: 26px;
}

.dm-section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.dm-badge {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #0f4726;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.dm-section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.dm-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: #374151;
  word-break: keep-all;
}

.dm-indent {
  margin-left: 42px;
}

.dm-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.dm-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #dfe5dc;
  border-radius: 999px;
  background: #f4f8f0;
  font-size: 13px;
  font-weight: 600;
  color: #0f4726;
}

.dm-pill-val {
  color: #374151;
  font-weight: 500;
}

.dm-skills {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dm-skill {
  display: flex;
  gap: 12px;
}

.dm-diamond {
  flex: none;
  margin-top: 7px;
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: #2f6f55;
  transform: rotate(45deg);
}

.dm-skill .dm-text {
  font-size: 14.5px;
  line-height: 1.7;
}

.dm-skill-lead {
  color: #0f4726;
  font-weight: 700;
}

.dm-highlight {
  padding: 22px 24px;
  border: 1px solid #e7efe2;
  border-radius: 12px;
  background: #f8faf6;
}

.dm-highlight-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 700;
  color: #0f4726;
}

.dm-scroll::-webkit-scrollbar {
  width: 12px;
}

.dm-scroll::-webkit-scrollbar-track {
  background: #f9f9f9;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb {
  border: 3px solid #f9f9f9;
  background: #bfbfbf75;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}
</style>
