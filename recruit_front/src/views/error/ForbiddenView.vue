<template>
  <div class="error-page">
    <a-result
      status="403"
      title="접근 권한이 없습니다."
      sub-title="요청하신 페이지에 접근할 권한이 없습니다. 로그인한 계정을 확인해주세요."
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
