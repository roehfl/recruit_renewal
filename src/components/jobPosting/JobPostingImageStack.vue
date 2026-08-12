<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type { JobPostingImage } from '@/types/jobPosting'

const props = defineProps<{
  images: JobPostingImage[]
  fetchImage: (imageId: number) => Promise<Blob>
}>()

const objectUrls = ref<Record<number, string>>({})
const failedIds = ref<Set<number>>(new Set())

const revokeAll = () => {
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url))
  objectUrls.value = {}
}

const loadImages = async (images: JobPostingImage[]) => {
  revokeAll()
  failedIds.value = new Set()
  for (const image of images) {
    try {
      const response = await props.fetchImage(image.id)
      objectUrls.value = { ...objectUrls.value, [image.id]: URL.createObjectURL(response) }
    } catch {
      failedIds.value = new Set([...failedIds.value, image.id])
    }
  }
}

watch(() => props.images, (images) => { void loadImages(images) }, { immediate: true })
onBeforeUnmount(revokeAll)
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
