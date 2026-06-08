<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  MenuOutlined,
  CloseOutlined,
  RightOutlined,
  UserOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

import { useAuthStore } from '@/stores/authStore'
import { useMenuStore } from '@/stores/menuStore'
import logoImage from '@/assets/images/logo.png'
import type { MenuItem } from '@/types/menu'

const router = useRouter()
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

const goMenu = (path: string) => {
  menuOpen.value = false
  router.push(path)
}

const goNotice = () => {
  router.push('/applicant/notifications')
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
    <!-- 상단 헤더 -->
    <div class="header-top">
      <div class="header-inner top-inner">
        <RouterLink to="/applicant" class="brand-logo logo-stack">
          <img :src="logoImage" alt="신영증권 로고" class="logo-img" />
          <span class="brand-logo-text">
            <span class="brand-logo-main">신영증권</span>
            <span class="brand-logo-sub">Recruit</span>
          </span>
        </RouterLink>

        <div class="top-actions">
          <!-- 비로그인 상태 -->
          <template v-if="!authStore.isLoggedIn">
            <RouterLink to="/login" class="top-link"> 로그인 </RouterLink>

            <span class="top-divider" />

            <RouterLink to="/signup" class="top-link"> 회원가입 </RouterLink>
          </template>

          <!-- 로그인 상태 -->
          <template v-else>
            <a-button type="text" class="notice-button" aria-label="알림" @click="goNotice">
              <a-badge :dot="hasUnreadNotice">
                <!-- <BellOutlined /> -->
              </a-badge>
            </a-button>

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
                  <a-menu-item key="profile"> 내 정보 </a-menu-item>

                  <a-menu-item key="applications"> 지원 현황 </a-menu-item>

                  <a-menu-divider />

                  <a-menu-item key="logout"> 로그아웃 </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
        </div>
      </div>
    </div>

    <!-- 하단 메뉴 -->
    <div class="header-bottom">
      <div class="header-inner bottom-inner">
        <a-button
          type="text"
          class="hamburger-button"
          :aria-label="menuOpen ? '전체 메뉴 닫기' : '전체 메뉴 열기'"
          @click="toggleMenu"
        >
          <CloseOutlined v-if="menuOpen" />
          <MenuOutlined v-else />
        </a-button>

        <nav class="main-nav">
          <div v-for="menu in mainMenus" :key="menu.id" class="main-menu-wrapper">
            <RouterLink :to="menu.path ?? ''" class="main-menu-item">
              {{ menu.name }}
            </RouterLink>

            <!-- 햄버거 전체메뉴가 열려 있을 때는 hover 소메뉴 숨김 -->
            <div v-if="menu.children?.length && !menuOpen" class="sub-menu-panel">
              <button
                v-for="child in menu.children"
                :key="child.id"
                type="button"
                class="sub-menu-item"
                @click="goMenu(child.path ?? '')"
              >
                {{ child.name }}
              </button>
            </div>
          </div>
        </nav>
      </div>
    </div>

    <!-- Header 바로 아래 전체 메뉴 상세 -->
    <transition name="mega-menu">
      <div v-if="menuOpen" class="mega-menu">
        <div class="header-inner mega-menu-inner">
          <div v-for="menu in mainMenus" :key="menu.id" class="mega-menu-column">
            <button type="button" class="mega-menu-title" @click="goMenu(menu.path ?? '')">
              <span>{{ menu.name }}</span>
              <RightOutlined />
            </button>

            <ul v-if="menu.children?.length" class="mega-sub-menu">
              <li v-for="child in menu.children" :key="child.id">
                <button type="button" @click="goMenu(child.path ?? '')">
                  {{ child.name }}
                </button>
              </li>
            </ul>
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
  background: var(--app-content-bg-color);
  border-bottom: 1px solid var(--app-border-color);
}

.header-inner {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px;
}

.header-top {
  height: 72px;
  border-bottom: 1px solid var(--app-border-color);
  background: var(--app-bg-surface);
}

.top-inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
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
}

.logo-img {
  width: 4.8%;
  height: auto;
  object-fit: contain;
  flex: 0 0 auto;
  display: block;
}

.brand-logo-text {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0px;
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

.top-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: var(--app-text-color);
}

.top-link {
  color: var(--app-text-color);
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

/* 로그인 후 알림 버튼 */
.notice-button {
  width: 40px;
  height: 40px;
  padding: 0;
  color: var(--app-text-color);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  // font-size: 20px;
  //   border-radius: 50%;

  &:hover,
  &:focus,
  &:active {
    color: var(--app-primary-color);
    background: transparent !important;
  }

  :deep(.ant-badge) {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  :deep(.anticon) {
    font-size: 20px;
    color: var(--app-sub-text-color);
    line-height: 1;
  }
}

/* 로그인 후 사용자 버튼 */
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
    // color: var(--app-text-primary);

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
  // background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: color 0.15s ease;
  background: 0.15s ease;
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

.header-bottom {
  height: 58px;
  background: var(--app-bg-surface);
}

.bottom-inner {
  height: 100%;
  display: flex;
  align-items: center;
}

.hamburger-button {
  width: 44px;
  height: 44px;
  margin-right: 58px;
  border-radius: 8px;
  color: var(--app-text-color);
  font-size: 18px;
  font-weight: 700;

  &:hover,
  &:focus,
  &:active {
    color: var(--app-primary-color);
    background: transparent !important;
  }
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 38px;
  height: 100%;
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
  height: 58px;
  display: flex;
  align-items: center;
  color: var(--app-text-color);
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  transition: color 0.15s ease;

  &:hover {
    color: var(--app-primary-color);
  }

  &.router-link-active {
    color: var(--app-primary-color);
  }

  &.router-link-active::after {
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

/* 대메뉴 hover 시 표시되는 소메뉴 */
.sub-menu-panel {
  display: none;
  position: absolute;
  top: 58px;
  left: -16px;
  z-index: 220;

  min-width: 148px;
  padding: 12px 0;
  flex-direction: column;

  background: var(--app-bg-surface);
  border: 1px solid var(--app-border-color);
  border-radius: 4px;
  box-shadow: 0 8px 20px rgb(15 71 38 / 10%);
}

.sub-menu-item {
  width: 100%;
  height: 34px;
  padding: 0 16px;
  border: 0;
  background: var(--app-bg-surface);
  color: var(--app-sub-text-color);
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;

  &:hover {
    color: var(--app-primary-color);
    background: var(--app-bg-btn-hover);
  }
}

/* Header 아래 노출되는 전체 메뉴 */
.mega-menu {
  position: absolute;
  top: 130px;
  left: 0;
  right: 0;
  z-index: 90;
  background: var(--app-bg-surface);
  border-top: 1px solid var(--app-border-color);
  border-bottom: 1px solid var(--app-border-color);
  box-shadow: 0 12px 24px rgb(15 71 38 / 8%);
}

.mega-menu-inner {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 0;
  padding-top: 28px;
  padding-bottom: 32px;
}

.mega-menu-column {
  min-height: 180px;
  padding: 0 28px;
  border-right: 1px solid var(--app-divider-color);

  &:last-child {
    border-right: 0;
  }
}

.mega-menu-title {
  width: 100%;
  padding: 0 0 18px;
  border: 0;
  background: transparent;
  color: var(--app-text-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 17px;
  font-weight: 800;
  cursor: pointer;
  text-align: left;

  &:hover {
    color: var(--app-primary-color);
  }

  :deep(.anticon) {
    font-size: 12px;
  }
}

.mega-sub-menu {
  list-style: none;
  margin: 0;
  padding: 0;

  li + li {
    margin-top: 10px;
  }

  button {
    width: 100%;
    padding: 0;
    border: 0;
    background: transparent;
    color: var(--app-sub-text-color);
    font-size: 14px;
    line-height: 1.5;
    text-align: left;
    cursor: pointer;
    transition: color 0.15s ease;

    &:hover {
      color: var(--app-primary-color);
      // background: var(--app-bg-btn-hover);
    }
  }
}

/* 전체 메뉴 열림/닫힘 애니메이션 */
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

.mega-menu-enter-to,
.mega-menu-leave-from {
  opacity: 1;
  transform: translateY(0);
}

@media (max-width: 768px) {
  .header-top {
    height: 64px;
  }

  .logo-area img {
    width: 120px;
  }

  .header-bottom {
    height: 52px;
  }

  .hamburger-button {
    margin-right: 46px;
  }

  .main-nav {
    gap: 22px;
    overflow-x: auto;
  }

  .main-menu-item {
    height: 52px;
    font-size: 15px;
  }

  .sub-menu-panel {
    top: 52px;
    left: -12px;
  }

  .mega-menu {
    top: 116px;
  }

  .mega-menu-inner {
    grid-template-columns: 1fr;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .mega-menu-column {
    min-height: auto;
    padding: 16px 24px;
    border-right: 0;
    border-bottom: 1px solid var(--app-divider-color);

    &:last-child {
      border-bottom: 0;
    }
  }
}
</style>

<style lang="scss">
.user-dropdown-overlay {
  .ant-dropdown-menu {
    min-width: 168px;
    padding: 8px 0;
    background: var(--app-bg-surface);
    border: 1px solid var(--app-border-color);
    border-radius: 6px;
    box-shadow: 0 8px 20px rgb(15 71 38 / 10%);
  }

  .ant-dropdown-menu-item {
    height: 38px;
    padding: 0 18px;
    color: var(--app-text-color);
    font-size: 13px;
    display: flex;
    align-items: center;
  }

  .ant-dropdown-menu-item:hover {
    color: var(--app-primary-color);
    background: var(--app-bg-btn-hover) !important;
    border-radius: 0px;
  }

  .ant-dropdown-menu-item-danger {
    color: var(--app-danger-color);
  }
}
</style>
