<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  MenuOutlined,
  CloseOutlined,
  RightOutlined,
  BellOutlined,
  UserOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

import { useAuthStore } from '@/stores/authStore'
import { useMenuStore } from '@/stores/menuStore'
import logoImage from '@/assets/images/logo.png'
import type { MenuItem } from '@/types/menu'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const menuStore = useMenuStore()

const menuOpen = ref(false)
const hasUnreadNotice = ref(false)

const mainMenus = computed<MenuItem[]>(() => {
  return menuStore.getMenuTree('APPLICANT') ?? []
})

const loadMenuTree = async (): Promise<void> => {
  await menuStore.fetchMenuTree('APPLICANT')
}

onMounted(async () => {
  await loadMenuTree()
})

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value
}

const closeMenu = () => {
  menuOpen.value = false
}

const goMenu = (menu: MenuItem) => {
  closeMenu()

  if (!menu.path) {
    return
  }

  if (menu.type === 'URL') {
    window.open(menu.path, '_blank', 'noopener,noreferrer')
    return
  }

  router.push(menu.path)
}

const goNotice = () => {
  router.push('/applicant/notifications')
}

/*
 * 메뉴에 등록되지 않은 화면(공고 상세 등)은 route meta의 activeMenuPath로
 * 어느 메뉴를 활성으로 표시할지 지정한다.
 */
const activeMenuPath = computed<string>(() => {
  return route.meta.activeMenuPath ?? route.path
})

/*
 * 메뉴 자신의 type/path가 아니라 현재 경로의 메뉴 경로에 포함되는지로 판정한다.
 * 대메뉴를 URL 타입이나 path 없는 그룹으로 등록해도 표시가 깨지지 않는다.
 */
const isActiveMenu = (menu: MenuItem): boolean => {
  return menuStore.isActiveMenu('APPLICANT', menu, activeMenuPath.value)
}

const handleUserMenuClick = async ({ key }: { key: string }) => {
  if (key === 'profile') {
    router.push('/applicant/profile')
    return
  }

  if (key === 'applications') {
    router.push('/applicant/applications/my')
    return
  }

  if (key === 'logout') {
    await authStore.logout()
    router.replace('/applicant')
  }
}
</script>

<template>
  <header class="applicant-header">
    <div class="header-inner header-main">
      <div class="header-inner-left">
        <button
        type="button"
        class="hamburger-button"
        :aria-label="menuOpen ? '전체 메뉴 닫기' : '전체 메뉴 열기'"
        :aria-expanded="menuOpen"
        @click="toggleMenu"
        >
        <CloseOutlined v-if="menuOpen" />
        <MenuOutlined v-else />
        </button>
        <RouterLink to="/applicant" class="brand-logo logo-stack" @click="closeMenu">
          <img :src="logoImage" alt="신영증권 로고" class="logo-img" />
          <span class="brand-logo-text">
            <span class="brand-logo-main">신영증권</span>
            <span class="brand-logo-sub">Recruit</span>
          </span>
        </RouterLink>
      </div>
      

      <nav class="main-nav" aria-label="지원자 메인 메뉴">
        <div v-for="menu in mainMenus" :key="menu.id" class="main-menu-wrapper">
          <button
            type="button"
            class="main-menu-item"
            :class="{ active: isActiveMenu(menu) }"
            :aria-haspopup="menu.children?.length ? 'true' : undefined"
            @click="goMenu(menu)"
          >
            {{ menu.name }}
          </button>

          <div v-if="menu.children?.length && !menuOpen" class="sub-menu-panel">
            <button
              v-for="child in menu.children"
              :key="child.id"
              type="button"
              class="sub-menu-item"
              :class="{ active: isActiveMenu(child) }"
              @click="goMenu(child)"
            >
              {{ child.name }}
            </button>
          </div>
        </div>
      </nav>

      <div class="top-actions">
        <template v-if="!authStore.isLoggedIn">
          <RouterLink to="/login" class="top-link" @click="closeMenu"> 로그인 </RouterLink>
        </template>

        <template v-else>
          <a-dropdown :trigger="['click']" overlay-class-name="user-dropdown-overlay">
            <button type="button" class="user-menu-button">
              <span class="user-avatar">
                <UserOutlined />
              </span>

              <span class="user-name">
                {{ authStore.name }}
              </span>

              <DownOutlined class="user-down-icon" />
            </button>

            <template #overlay>
              <a-menu class="user-dropdown-menu" @click="handleUserMenuClick">
                <a-menu-item key="profile"> 마이페이지 </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout"> 로그아웃 </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
      </div>
    </div>

    <transition name="mega-menu">
      <div v-if="menuOpen" class="mega-menu">
        <div class="header-inner mega-menu-inner">
          <div v-for="menu in mainMenus" :key="menu.id" class="mega-menu-column">
            <button
              type="button"
              class="mega-menu-title"
              :class="{ active: isActiveMenu(menu) }"
              @click="goMenu(menu)"
            >
              <span>{{ menu.name }}</span>
              <RightOutlined v-if="menu.path" />
            </button>

            <ul v-if="menu.children?.length" class="mega-sub-menu">
              <li v-for="child in menu.children" :key="child.id">
                <button
                  type="button"
                  :class="{ active: isActiveMenu(child) }"
                  @click="goMenu(child)"
                >
                  {{ child.name }}
                </button>
              </li>
            </ul>
          </div>

          <div class="mega-mobile-actions">
            <template v-if="!authStore.isLoggedIn">
              <RouterLink to="/login" class="mega-action-link" @click="closeMenu"> 로그인 </RouterLink>
              <RouterLink to="/signup" class="mega-action-link primary" @click="closeMenu">
                회원가입
              </RouterLink>
            </template>

            <template v-else>
              <button type="button" class="mega-action-link" @click="router.push('/applicant/profile')">
                내 정보
              </button>
              <button
                type="button"
                class="mega-action-link primary"
                @click="router.push('/applicant/applications/my')"
              >
                지원 현황
              </button>
            </template>
          </div>
        </div>
      </div>
    </transition>
  </header>
</template>

<style scoped lang="scss">
.applicant-header {
  position: relative;
  z-index: 100;
  width: 100%;
  background: var(--app-bg-surface);
  border-bottom: 1px solid var(--app-border-color);
}

.header-inner {
  width: 100%;
  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 0 24px;
}

.header-main {
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 22px;
}

.header-inner-left {
  display: flex;
  margin-right: 30px;
}

.brand-logo {
  text-decoration: none;
  white-space: nowrap;
  font-family: 'Pretendard', sans-serif;
}

.logo-stack {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  line-height: 1;
  flex: 0 0 auto;
}

.logo-img {
  width: 30px;
  height: auto;
  object-fit: contain;
  display: block;
}

.brand-logo-text {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  line-height: 1;
}

.logo-stack .brand-logo-main {
  font-size: 26px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1;
  letter-spacing: -0.045em;
}

.logo-stack .brand-logo-sub {
  color: var(--app-primary-color);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  line-height: 1;
}

.hamburger-button {
  width: 44px;
  height: 44px;
  margin-right: 10px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-color);
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;

  &:hover,
  &:focus,
  &:active {
    color: var(--app-primary-color);
    background: var(--app-bg-btn-hover);
  }
}

.main-nav {
  height: 72px;
  display: flex;
  align-items: center;
  margin-left: 20px;
  gap: 38px;
  flex: 1 1 auto;
  min-width: 0;
}

.main-menu-wrapper {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;

  &:hover {
    .main-menu-item {
      color: var(--app-primary-color);
    }

    .main-menu-item::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 3px;
      border-radius: 999px;
      background: var(--app-primary-color);
    }

    .sub-menu-panel {
      display: flex;
    }
  }
}

.main-menu-item {
  position: relative;
  height: 72px;
  padding: 0;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  color: var(--app-text-color);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.15s ease;

  &:hover,
  &.active {
    color: var(--app-primary-color);
  }

  &.active::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: -1px;
    height: 3px;
    border-radius: 999px;
    background: var(--app-primary-color);
  }
}

.sub-menu-panel {
  position: absolute;
  top: 73px;
  left: 50%;
  display: none;
  min-width: 180px;
  padding: 10px;
  flex-direction: column;
  gap: 4px;
  transform: translateX(-50%);
  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-color);
  border-radius: 4px;
  box-shadow: var(--app-box-shadow);
}

.sub-menu-item {
  width: 100%;
  min-height: 38px;
  padding: 8px 12px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--app-text-color);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;

  &:hover,
  &.active {
    color: var(--app-primary-color);
    background: var(--app-bg-btn-hover);
  }
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: var(--app-text-color);
  flex: 0 0 auto;
}

.top-link {
  margin-right: 20px;
  color: var(--app-text-color);
  font-weight: 600;
  text-decoration: none;
  transition: color 0.15s ease;

  &:hover {
    color: var(--app-primary-color);
  }
}

.top-divider {
  width: 1px;
  height: 12px;
  background: var(--app-border-dark-color);
}

.notice-button {
  width: 40px;
  height: 40px;
  padding: 0;
  color: var(--app-text-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover,
  &:focus,
  &:active {
    color: var(--app-primary-color);
    background: var(--app-bg-btn-hover) !important;
  }

  :deep(.ant-badge) {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  :deep(.anticon) {
    font-size: 19px;
    color: var(--app-text-secondary);
    line-height: 1;
  }
}

.user-menu-button {
  height: 36px;
  padding: 0;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--app-text-primary);
  cursor: pointer;

  &:hover {
    .user-avatar {
      background: var(--app-bg-btn-hover);
      color: var(--app-primary-color);
    }
  }
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--app-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.user-name {
  max-width: 80px;
  font-size: 13px;
  font-weight: 700;
  color: inherit;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.user-down-icon {
  font-size: 10px;
  color: var(--app-sub-text-color);
}

.mega-menu {
  position: absolute;
  top: 72px;
  left: 0;
  right: 0;
  background: var(--app-bg-surface);
  border-bottom: 1px solid var(--app-border-color);
  box-shadow: var(--app-box-shadow);
}

.mega-menu-inner {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 24px;
  padding-top: 28px;
  padding-bottom: 32px;
}

.mega-menu-column {
  min-width: 0;
}

.mega-menu-title {
  width: 100%;
  padding: 0 0 12px;
  border: 0;
  border-bottom: 1px solid var(--app-border-color);
  background: transparent;
  color: var(--app-text-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-family: inherit;
  font-size: 16px;
  font-weight: 800;
  text-align: left;
  cursor: pointer;

  &:hover,
  &.active {
    color: var(--app-primary-color);
  }

  :deep(.anticon) {
    font-size: 11px;
  }
}

.mega-sub-menu {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;

  button {
    width: 100%;
    min-height: 34px;
    padding: 6px 0;
    border: 0;
    background: transparent;
    color: var(--app-text-color);
    font-family: inherit;
    font-size: 14px;
    font-weight: 600;
    text-align: left;
    cursor: pointer;

    &:hover,
    &.active {
      color: var(--app-primary-color);
    }
  }
}

.mega-mobile-actions {
  display: none;
}

.mega-menu-enter-active,
.mega-menu-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}

.mega-menu-enter-from,
.mega-menu-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 960px) {
  .header-main {
    gap: 14px;
  }

  .main-nav {
    display: none;
  }

  .hamburger-button {
    margin-left: auto;
  }

  .top-actions {
    margin-left: 0;
  }

  .mega-menu-inner {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}

@media (max-width: 640px) {
  .header-inner {
    padding: 0 18px;
  }

  .header-main {
    min-height: 64px;
  }

  .logo-img {
    width: 34px;
  }

  .logo-stack .brand-logo-main {
    font-size: 20px;
  }

  .logo-stack .brand-logo-sub {
    font-size: 10px;
  }

  .top-actions {
    display: none;
  }

  .mega-menu {
    top: 64px;
  }

  .mega-menu-inner {
    padding-top: 22px;
    padding-bottom: 24px;
  }

  .mega-mobile-actions {
    display: flex;
    gap: 10px;
    padding-top: 8px;
  }

  .mega-action-link {
    flex: 1;
    min-height: 40px;
    padding: 0 12px;
    border: 1px solid var(--app-border-color);
    border-radius: 10px;
    background: var(--app-bg-surface);
    color: var(--app-text-color);
    font-family: inherit;
    font-size: 14px;
    font-weight: 700;
    text-decoration: none;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;

    &.primary {
      border-color: var(--app-primary-color);
      background: var(--app-primary-color);
      color: #fff;
    }
  }
}
</style>
