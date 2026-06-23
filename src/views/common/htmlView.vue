<template>
  
    <div class="html-content" v-html="content"></div>
    
</template>

<script setup lang="ts">

interface Props { 
  // mode: 'page' | 'modal'
  content: string
  title?: string
  open?: boolean
  color?: string
}

const props = defineProps<Props>()


</script>

<style scoped>
.html-content {

}

/* 제목 */
.html-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 20px;
}
.html-content :deep(h3) {
  font-size: 18px;
  font-weight: 600;
  margin: 10px 0;
}

/* 문단 */
.html-content :deep(p) {
  font-size: 15px;
  margin-bottom: 8px;
}

/* 리스트 */
.html-content :deep(ul) {
  /* font-size: 15px; */
}
</style>
