<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'

import { ADMIN_MENU_ICONS } from '@/common/antIcon'

/*
 * 관리자 메뉴(menu.icon)에 지정 가능한 아이콘 허용 목록을 눈으로 확인하는 화면.
 * ADMIN_MENU_ICONS가 단일 출처이므로 여기서 보이는 이름이 곧 DB에 넣을 수 있는 값이다.
 */
const iconEntries = Object.entries(ADMIN_MENU_ICONS)

const keyword = ref('')
const onSidebarBackground = ref(false)

const filteredEntries = computed(() => {
  const trimmedKeyword = keyword.value.trim().toLowerCase()

  if (!trimmedKeyword) {
    return iconEntries
  }

  return iconEntries.filter(([iconName]) => iconName.toLowerCase().includes(trimmedKeyword))
})

const copyIconName = async (iconName: string): Promise<void> => {
  try {
    await navigator.clipboard.writeText(iconName)
    message.success(`${iconName} 복사됨`)
  } catch {
    message.warning('클립보드 복사에 실패했습니다. 이름을 직접 입력하세요.')
  }
}
</script>

<template>
  <div class="icon-preview">
    <header class="preview-header">
      <h2 class="preview-title">
        관리자 메뉴 아이콘
        <span class="preview-count">총 {{ iconEntries.length }}종</span>
      </h2>
      <p class="preview-description">
        <code>menu.icon</code> 컬럼에 넣을 수 있는 값 목록입니다. 아이콘을 클릭하면 이름이 복사됩니다.
        목록에 없는 이름을 저장하면 <code>AppstoreOutlined</code>로 대체되어 표시됩니다.
      </p>
    </header>

    <div class="preview-toolbar">
      <a-input
        v-model:value="keyword"
        class="preview-search"
        placeholder="아이콘 이름 검색 (예: file, user, chart)"
        allow-clear
      />
      <a-switch v-model:checked="onSidebarBackground" />
      <span class="toolbar-label">사이드바 배경에서 보기</span>
      <span class="toolbar-result">{{ filteredEntries.length }}개 표시</span>
    </div>

    <ul v-if="filteredEntries.length" class="icon-grid" :class="{ 'on-dark': onSidebarBackground }">
      <li v-for="[iconName, iconComponent] in filteredEntries" :key="iconName">
        <button type="button" class="icon-card" :title="`${iconName} 복사`" @click="copyIconName(iconName)">
          <span class="icon-symbol">
            <component :is="iconComponent" />
          </span>
          <span class="icon-name">{{ iconName }}</span>
        </button>
      </li>
    </ul>

    <p v-else class="preview-empty">
      "{{ keyword }}"와 일치하는 아이콘이 없습니다. 필요한 아이콘은
      <code>src/common/antIcon.ts</code>에 추가할 수 있습니다.
    </p>
  </div>
</template>

<style scoped lang="scss">
.icon-preview {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 24px 56px;
}

.preview-title {
  margin: 0 0 6px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 22px;
  font-weight: 800;
  color: var(--app-text-primary);
  letter-spacing: -0.02em;
}

.preview-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-muted);
}

.preview-description {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
    font-size: 12px;
  }
}

.preview-toolbar {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 20px 0 16px;
  padding: 12px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--app-bg-surface);
}

.preview-search {
  max-width: 320px;
}

.toolbar-label {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.toolbar-result {
  margin-left: auto;
  font-size: 12px;
  color: var(--app-text-muted);
}

/*
 * 카드 색은 CSS 변수로 넘긴다. 변수는 상속되므로 밝은/어두운 배경 전환에
 * 선택자 특이도 다툼이 생기지 않는다.
 */
.icon-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  gap: 10px;

  --icon-card-border: var(--app-border-default);
  --icon-card-bg: var(--app-bg-surface);
  --icon-card-fg: var(--app-text-primary);
  --icon-card-name-fg: var(--app-text-secondary);
  --icon-card-border-hover: var(--app-color-primary);
  --icon-card-bg-hover: var(--app-bg-selected);
  --icon-card-fg-hover: var(--app-color-primary);

  /* 실제로는 다크 그린 사이드바 위에 올라가므로 대비 확인용 배경을 제공한다. */
  &.on-dark {
    padding: 14px;
    border-radius: var(--app-border-radius-lg);
    background: linear-gradient(180deg, var(--app-color-primary) 0%, #0a3a1e 100%);

    --icon-card-border: rgb(255 255 255 / 12%);
    --icon-card-bg: transparent;
    --icon-card-fg: rgb(255 255 255 / 74%);
    --icon-card-name-fg: rgb(255 255 255 / 55%);
    --icon-card-border-hover: rgb(255 255 255 / 30%);
    --icon-card-bg-hover: rgb(255 255 255 / 8%);
    --icon-card-fg-hover: #fff;
  }
}

.icon-card {
  width: 100%;
  padding: 16px 8px 12px;
  border: 1px solid var(--icon-card-border);
  border-radius: var(--app-border-radius);
  background: var(--icon-card-bg);
  color: var(--icon-card-fg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-family: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;

  &:hover {
    border-color: var(--icon-card-border-hover);
    background: var(--icon-card-bg-hover);
    color: var(--icon-card-fg-hover);
  }
}

.icon-symbol {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  :deep(.anticon) {
    font-size: 22px;
    line-height: 1;
  }
}

.icon-name {
  width: 100%;
  font-size: 11px;
  font-weight: 600;
  color: var(--icon-card-name-fg);
  text-align: center;
  word-break: break-all;
  line-height: 1.35;
}

.preview-empty {
  padding: 48px 0;
  font-size: 13px;
  color: var(--app-text-muted);
  text-align: center;

  code {
    color: var(--app-text-secondary);
  }
}
</style>
