<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type { JobPostingImage } from '@/types/jobPosting'

const props = defineProps<{
  images: JobPostingImage[]
  fetchImage: (imageId: number) => Promise<Blob>
}>()

const objectUrls = ref<Record<number, string>>({})
const failedIds = ref<Set<number>>(new Set())

// images 변경/언마운트 시 세대를 올려, 진행 중이던 병렬 로딩이 stale objectURL을 남기지 않게 한다.
let loadGeneration = 0

const revokeAll = () => {
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url))
  objectUrls.value = {}
}

const loadImages = async (images: JobPostingImage[]) => {
  const generation = ++loadGeneration
  revokeAll()
  failedIds.value = new Set()
  await Promise.all(images.map(async (image) => {
    try {
      const response = await props.fetchImage(image.id)
      const url = URL.createObjectURL(response)
      if (generation !== loadGeneration) {
        URL.revokeObjectURL(url)
        return
      }
      objectUrls.value = { ...objectUrls.value, [image.id]: url }
    } catch {
      if (generation === loadGeneration) {
        failedIds.value = new Set([...failedIds.value, image.id])
      }
    }
  }))
}

watch(() => props.images, (images) => { void loadImages(images) }, { immediate: true })
onBeforeUnmount(() => {
  loadGeneration++
  revokeAll()
})
</script>

<template>
  <div class="posting-image-stack">
    <template v-for="image in images" :key="image.id">
      <img
        v-if="objectUrls[image.id]"
        :src="objectUrls[image.id]"
        :alt="image.altText"
        class="posting-image"
      />
      <p v-else-if="failedIds.has(image.id)" class="image-error">이미지를 불러오지 못했습니다.</p>
    </template>
  </div>
</template>

<style scoped>
.posting-image-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}
.posting-image {
  display: block;
  max-width: 100%;
  height: auto;
}
.image-error {
  color: #999;
  padding: 16px 0;
}
</style>
