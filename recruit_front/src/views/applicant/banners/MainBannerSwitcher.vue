<template>
  <section class="main-banner-switcher">
    <img
      v-if="bannerImageSrc"
      :src="bannerImageSrc"
      alt="신영증권 채용 배너"
      class="banner-image"
      @error="onBannerImageError"
    />

    <!-- public/images/main-banner.* 가 없을 때의 폴백 -->
    <template v-else>
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
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import MainBanner1 from '@/views/applicant/banners/MainBanner1.vue'
import MainBanner2 from '@/views/applicant/banners/MainBanner2.vue'
import MainBanner3 from '@/views/applicant/banners/MainBanner3.vue'
import MainBanner4 from '@/views/applicant/banners/MainBanner4.vue'
import MainBanner5 from '@/views/applicant/banners/MainBanner5.vue'

// 배너 추가 시: banners/MainBannerN.vue 생성 후 이 배열에만 추가하면 된다.
const banners = [MainBanner1, MainBanner2, MainBanner3, MainBanner4, MainBanner5]
const selected = ref(0)

/*
 * 메인 배너 이미지: public/images/main-banner.{png,jpg,svg,webp} 중 하나를 추가하면
 * 별도 코드 수정 없이 적용된다. 파일이 없으면 위 컴포넌트 배너로 폴백한다.
 */
const bannerImageCandidates = [
  '/images/main-banner.png',
  '/images/main-banner.jpg',
  '/images/main-banner.svg',
  '/images/main-banner.webp',
]
const bannerImageIndex = ref(0)

const bannerImageSrc = computed<string>(() => bannerImageCandidates[bannerImageIndex.value] ?? '')

const onBannerImageError = (): void => {
  bannerImageIndex.value += 1
}
</script>

<style scoped>
.main-banner-switcher {
  position: relative;
}

.banner-image {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 8px;
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
