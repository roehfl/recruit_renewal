<template>
  <section class="main-banner-switcher">
    <component :is="banners[selected]" />
    <div class="banner-picker">
      <button
        v-for="(_, index) in banners"
        :key="index"
        type="button"
        class="picker-chip"
        :class="{ active: index === selected }"
        @click="selected = index"
      >
        {{ index + 1 }}
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import MainBanner1 from '@/views/applicant/banners/MainBanner1.vue'
import MainBanner2 from '@/views/applicant/banners/MainBanner2.vue'
import MainBanner3 from '@/views/applicant/banners/MainBanner3.vue'
import MainBanner4 from '@/views/applicant/banners/MainBanner4.vue'
import MainBanner5 from '@/views/applicant/banners/MainBanner5.vue'

// 배너 추가 시: banners/MainBannerN.vue 생성 후 이 배열에만 추가하면 된다.
const banners = [MainBanner1, MainBanner2, MainBanner3, MainBanner4, MainBanner5]
const selected = ref(0)
</script>

<style scoped>
.main-banner-switcher {
  position: relative;
}

.banner-picker {
  position: absolute;
  right: 14px;
  bottom: 12px;
  display: flex;
  gap: 6px;
}

.picker-chip {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid rgba(47, 111, 85, 0.35);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  color: #2f6f55;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}

.picker-chip:hover {
  background: #ffffff;
  border-color: #2f6f55;
}

.picker-chip.active {
  background: #2f6f55;
  border-color: #2f6f55;
  color: #ffffff;
}
</style>
