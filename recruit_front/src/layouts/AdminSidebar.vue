<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogoutOutlined } from '@ant-design/icons-vue'

import { useAuthStore } from '@/stores/authStore'
import { useMenuStore } from '@/stores/menuStore'
import { resolveAntIconOrFallback } from '@/common/antIcon'
import logoImage from '@/assets/images/logo.png'
import type { MenuItem } from '@/types/menu'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const menuStore = useMenuStore()

/*
 * 관리자 사이드바는 2단계 메뉴를 쓴다.
 * 대메뉴(path 없는 그룹)는 소제목 라벨로만 표시하고, 실제 이동 대상은 아이콘과 함께 표시되는 소메뉴다.
 */
const menuGroups = computed<MenuItem[]>(() => {
  return menuStore.getMenuTree('ADMIN') ?? []
})

onMounted(async () => {
  await menuStore.fetchMenuTree('ADMIN')
})

/*
 * 메뉴에 등록되지 않은 화면(상세 페이지 등)은 route meta의 activeMenuPath로
 * 어느 메뉴를 활성으로 표시할지 지정한다. 지원자 헤더와 같은 규약이다.
 */
const activeMenuPath = computed<string>(() => {
  return route.meta.activeMenuPath ?? route.path
})

const isActiveMenu = (menu: MenuItem): boolean => {
  return menuStore.isActiveMenu('ADMIN', menu, activeMenuPath.value)
}

const goMenu = (menu: MenuItem): void => {
  if (!menu.path) {
    return
  }

  if (menu.type === 'URL') {
    window.open(menu.path, '_blank', 'noopener,noreferrer')
    return
  }

  router.push(menu.path)
}

const userInitial = computed<string>(() => {
  return authStore.name.charAt(0) || '관'
})

const handleLogout = async (): Promise<void> => {
  await authStore.logout()
  menuStore.clearMenuTree('ADMIN')
  router.replace('/login')
}
</script>

<template>
  <aside class="admin-sidebar">
    <div class="sidebar-brand">
      <span class="brand-logo">
        <img :src="logoImage" alt="신영증권 로고" />
      </span>
      <span class="brand-text">
        <span class="brand-name">신영증권</span>
        <span class="brand-sub">RECRUIT ADMIN</span>
      </span>
    </div>

    <nav class="sidebar-nav" aria-label="관리자 메인 메뉴">
      <div v-for="group in menuGroups" :key="group.id" class="nav-group">
        <template v-if="group.children?.length">
          <p class="nav-group-label">{{ group.name }}</p>

          <button
            v-for="menu in group.children"
            :key="menu.id"
            type="button"
            class="nav-item"
            :class="{ active: isActiveMenu(menu) }"
            :aria-current="isActiveMenu(menu) ? 'page' : undefined"
            @click="goMenu(menu)"
          >
            <span class="nav-item-icon">
              <component :is="resolveAntIconOrFallback(menu.icon)" />
            </span>
            <span class="nav-item-name">{{ menu.name }}</span>
          </button>
        </template>

        <!-- 하위 메뉴가 없는 대메뉴는 라벨만 남지 않도록 그 자체를 이동 항목으로 표시한다. -->
        <button
          v-else
          type="button"
          class="nav-item"
          :class="{ active: isActiveMenu(group) }"
          :aria-current="isActiveMenu(group) ? 'page' : undefined"
          @click="goMenu(group)"
        >
          <span class="nav-item-icon">
            <component :is="resolveAntIconOrFallback(group.icon)" />
          </span>
          <span class="nav-item-name">{{ group.name }}</span>
        </button>
      </div>
    </nav>

    <div class="sidebar-user">
      <span class="user-avatar">{{ userInitial }}</span>
      <span class="user-text">
        <span class="user-name">{{ authStore.name }}</span>
        <span class="user-dept">{{ authStore.deptName }}</span>
      </span>
      <button type="button" class="logout-button" aria-label="로그아웃" @click="handleLogout">
        <LogoutOutlined />
      </button>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.admin-sidebar {
  width: var(--app-sidebar-width);
  flex: none;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--app-color-primary) 0%, #0a3a1e 100%);
}

.sidebar-brand {
  height: 56px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 18px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
}

.brand-logo {
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: 7px;
  background: var(--app-bg-surface);
  display: inline-flex;
  align-items: center;
  justify-content: center;

  img {
    width: 18px;
    height: 18px;
    object-fit: contain;
  }
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.03em;
}

.brand-sub {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #9fd3b5;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 12px 8px;
  scrollbar-width: thin;
  scrollbar-color: rgb(255 255 255 / 18%) transparent;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: rgb(255 255 255 / 18%);
  }
}

.nav-group {
  margin-bottom: 13px;
}

.nav-group-label {
  margin: 0;
  padding: 0 10px 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: rgb(255 255 255 / 42%);
}

.nav-item {
  position: relative;
  width: 100%;
  height: 36px;
  margin-bottom: 2px;
  padding: 0 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: rgb(255 255 255 / 74%);
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;

  &:hover {
    background: rgb(255 255 255 / 8%);
    color: #fff;
  }

  &.active {
    background: rgb(255 255 255 / 16%);
    color: #fff;
    font-weight: 700;
  }
}

.nav-item-icon {
  width: 18px;
  height: 18px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  :deep(.anticon) {
    font-size: 16px;
    line-height: 1;
  }
}

.nav-item-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-user {
  flex: none;
  padding: 10px 12px;
  border-top: 1px solid rgb(255 255 255 / 10%);
  display: flex;
  align-items: center;
  gap: 9px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 50%;
  background: rgb(255 255 255 / 16%);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.user-name {
  font-size: 12.5px;
  font-weight: 700;
  color: #fff;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.user-dept {
  font-size: 10.5px;
  color: rgb(255 255 255 / 50%);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.logout-button {
  margin-left: auto;
  padding: 4px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: rgb(255 255 255 / 60%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;

  &:hover {
    background: rgb(255 255 255 / 12%);
    color: #fff;
  }
}
</style>
