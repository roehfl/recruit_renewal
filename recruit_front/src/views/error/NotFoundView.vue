<template>
  <div class="error-page">
    <a-result
      status="404"
      title="페이지를 찾을 수 없습니다."
      sub-title="주소가 잘못되었거나 더 이상 제공되지 않는 페이지입니다."
    >
      <template #extra>
        <a-button type="primary" @click="goHome">홈으로</a-button>
        <a-button @click="goBack">이전 페이지</a-button>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => router.push('/applicant')
const goBack = () => router.back()
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 16px;
  background: #ffffff;
}
</style>
