<template>
  <nav class="app-breadcrumb" aria-label="현재 위치">
    <RouterLink to="/" class="breadcrumb-link"> 홈 </RouterLink>

    <span v-if="breadcrumbItems.length > 0" class="breadcrumb-separator"> › </span>

    <template v-for="(item, index) in breadcrumbItems" :key="item.id">
      <RouterLink v-if="isLink(item, index)" :to="item.path!" class="breadcrumb-link">
        {{ item.name }}
      </RouterLink>

      <span v-else class="breadcrumb-current">
        {{ item.name }}
      </span>

      <span v-if="index !== breadcrumbItems.length - 1" class="breadcrumb-separator"> › </span>
    </template>
  </nav>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useMenuStore } from '@/stores/menuStore'
import type { MenuItem } from '@/types/menu'

const route = useRoute()
const menuStore = useMenuStore()

const currentPath = computed(() => route.path)

const breadcrumbItems = computed<MenuItem[]>(() => {
  return menuStore.getBreadcrumb('APPLICANT', currentPath.value)
})

const loadBreadcrumb = async (): Promise<void> => {
  await menuStore.fetchBreadcrumb('APPLICANT', currentPath.value)
}

onMounted(async () => {
  await loadBreadcrumb()
})

const isLink = (item: MenuItem, index: number): boolean => {
  const isLast = index === breadcrumbItems.value.length - 1

  return !isLast && item.type === 'ROUTE' && !!item.path
}
</script>

<style scoped>
.app-breadcrumb {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  min-height: 36px;
  padding: 0 24px;

  background: var(--app-primary-subtle-color);
  /* border-top: 1px solid var(--app-border-color); */
  /* border-bottom: 1px solid var(--app-border-color); */
  border-radius: 0;

  color: #374151;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: -0.02em;

  width: 100%;
  margin-bottom: 20px;
}

.breadcrumb-link {
  color: #6b7280;
  text-decoration: none;
  transition: color 0.15s ease;
}

.breadcrumb-link:hover {
  color: #536d2f;
}

.breadcrumb-current {
  color: #536d2f;
  font-weight: 700;
}

.breadcrumb-separator {
  color: #a0a78f;
}
</style>
