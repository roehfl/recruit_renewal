<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <!-- <header>
    <img alt="Vue logo" class="logo" src="@/assets/logo.svg" width="125" height="125" />

    <div class="wrapper">
      <HelloWorld msg="You did it!" />

      <nav>
        <RouterLink to="/">Home</RouterLink>
        <RouterLink to="/about">About</RouterLink>
      </nav>
    </div>
  </header> -->

  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#0f4726',
        colorLink: '#0f4726',
        borderRadius: 8,
        colorInfo: '#1677ff',
        colorSuccess: '#2f855a',
        colorWarning: '#d97706',
        colorError: '#ff4d4f',
        /* 성공 배너·태그 배경. 기본값은 진한 colorSuccess 에서 파생돼 회녹색으로 칙칙하다. */
        colorSuccessBg: '#eaf7ee',
        colorSuccessBgHover: '#dcf1e4',
        colorSuccessBorder: '#a8dcbd',
        colorSuccessBorderHover: '#8ccfa8',
        /* 선택·hover 배경. 기본값은 진한 colorPrimary 에서 파생돼 칙칙하다(셀렉트 목록·표 선택 행 공통). */
        controlItemBgActive: '#f4f8f0',
        controlItemBgActiveHover: '#e8f0de',
        controlItemBgHover: '#f8faf6',
        fontFamily: 'Noto Sans KR, Noto Sans CJK KR, Apple SD Gothic Neo, sans-serif',
      },
    }"
  >
    <RouterView />
  </a-config-provider>
</template>

<style scoped>
header {
  line-height: 1.5;
  max-height: 100vh;
}

.logo {
  display: block;
  margin: 0 auto 2rem;
}

nav {
  width: 100%;
  font-size: 12px;
  text-align: center;
  margin-top: 2rem;
}

nav a.router-link-exact-active {
  color: var(--color-text);
}

nav a.router-link-exact-active:hover {
  background-color: transparent;
}

nav a {
  display: inline-block;
  padding: 0 1rem;
  border-left: 1px solid var(--color-border);
}

nav a:first-of-type {
  border: 0;
}

@media (min-width: 1024px) {
  header {
    display: flex;
    place-items: center;
    padding-right: calc(var(--section-gap) / 2);
  }

  .logo {
    margin: 0 2rem 0 0;
  }

  header .wrapper {
    display: flex;
    place-items: flex-start;
    flex-wrap: wrap;
  }

  nav {
    text-align: left;
    margin-left: -1rem;
    font-size: 1rem;

    padding: 1rem 0;
    margin-top: 1rem;
  }
}
</style>
