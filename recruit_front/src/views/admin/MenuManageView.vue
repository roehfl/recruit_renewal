<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { menuApi } from '@/api/menuApi'
import { getApiErrorMessage } from '@/api/apiError'
import { useMenuStore } from '@/stores/menuStore'
import { ADMIN_MENU_ICONS, resolveAntIconOrFallback } from '@/common/antIcon'
import type { MenuItem, MenuSaveRequest, MenuSite, MenuType } from '@/types/menu'

/*
 * 메뉴 관리는 "메인메뉴 › 서브메뉴 › 상세" 3단 컬럼으로 구성한다(설계 시안 1c).
 * 메뉴 트리가 2단으로 고정이고 서브메뉴는 저장된 메인메뉴 아래에만 붙을 수 있다는 규칙이
 * 화면 구조로 그대로 드러난다.
 */

type EditLevel = 'MAIN' | 'SUB'

interface EditTarget {
  level: EditLevel
  /* null이면 신규 등록, 값이 있으면 해당 메뉴 수정. 한 번에 하나만 편집한다. */
  menuId: number | null
}

interface MenuForm {
  name: string
  type: MenuType
  path: string
  sortOrder: number | null
  icon: string | null
}

const SITE_TABS: { key: MenuSite; label: string }[] = [
  { key: 'APPLICANT', label: '지원자' },
  { key: 'ADMIN', label: '관리자' },
]

/* 아이콘 선택지는 사이드바가 실제로 해석할 수 있는 허용 목록(ADMIN_MENU_ICONS)이 단일 출처다. */
const ICON_ENTRIES = Object.entries(ADMIN_MENU_ICONS)

const menuStore = useMenuStore()

const activeSite = ref<MenuSite>('APPLICANT')
const selectedMainId = ref<number | null>(null)
const editTarget = ref<EditTarget | null>(null)
const iconKeyword = ref('')
const saving = ref(false)

const createEmptyForm = (): MenuForm => ({
  name: '',
  type: 'ROUTE',
  path: '',
  sortOrder: null,
  icon: null,
})

const form = ref<MenuForm>(createEmptyForm())

const mainMenus = computed<MenuItem[]>(() => menuStore.getMenuTree(activeSite.value))

const selectedMain = computed<MenuItem | null>(() => {
  return mainMenus.value.find((menu) => menu.id === selectedMainId.value) ?? null
})

const subMenus = computed<MenuItem[]>(() => selectedMain.value?.children ?? [])

const isCreatingMain = computed<boolean>(() => {
  return editTarget.value?.level === 'MAIN' && editTarget.value.menuId === null
})

const isCreatingSub = computed<boolean>(() => {
  return editTarget.value?.level === 'SUB' && editTarget.value.menuId === null
})

const editingSubId = computed<number | null>(() => {
  return editTarget.value?.level === 'SUB' ? editTarget.value.menuId : null
})

/* 서브메뉴는 저장된 메인메뉴가 있어야 부모를 지정할 수 있다. */
const canAddSub = computed<boolean>(() => selectedMainId.value !== null)

/* 아이콘은 관리자 사이드바에서만 쓰이므로 관리자 탭의 서브메뉴에서만 고르게 한다. */
const isIconPickerVisible = computed<boolean>(() => {
  return activeSite.value === 'ADMIN' && editTarget.value?.level === 'SUB'
})

const filteredIcons = computed(() => {
  const keyword = iconKeyword.value.trim().toLowerCase()

  if (!keyword) {
    return ICON_ENTRIES
  }

  return ICON_ENTRIES.filter(([iconName]) => iconName.toLowerCase().includes(keyword))
})

const formTitle = computed<string>(() => {
  if (!editTarget.value) {
    return ''
  }

  const kind = editTarget.value.level === 'MAIN' ? '메인메뉴' : '서브메뉴'

  return editTarget.value.menuId === null ? `${kind} 신규 등록` : `${kind} 수정`
})

const formKind = computed<string>(() => {
  return editTarget.value?.level === 'SUB' ? '서브메뉴' : '메인메뉴'
})

const formIdText = computed<string>(() => {
  return editTarget.value?.menuId === null ? '신규' : String(editTarget.value?.menuId ?? '')
})

const saveText = computed<string>(() => {
  return editTarget.value?.menuId === null ? '등록' : '저장'
})

const parentLabel = computed<string>(() => {
  if (editTarget.value?.level !== 'SUB') {
    return '없음 (최상위)'
  }

  return selectedMain.value?.name ?? '—'
})

const pathPlaceholder = computed<string>(() => {
  return form.value.type === 'URL' ? 'https://example.com' : '/admin/example'
})

const formHint = computed<string>(() => {
  if (editTarget.value?.level === 'MAIN') {
    return '메인메뉴는 경로를 비우면 이동하지 않는 그룹 라벨이 됩니다.'
  }

  return '서브메뉴는 실제 이동 대상이므로 경로가 필요합니다.'
})

const shortIconName = (iconName: string): string => iconName.replace(/Outlined$/, '')

/* 신규 등록의 기본 정렬값은 같은 목록의 마지막 다음 순번으로 둔다(비워두면 목록 앞으로 올라온다). */
const nextSortOrder = (menus: MenuItem[]): number => {
  if (!menus.length) {
    return 1
  }

  return Math.max(...menus.map((menu) => menu.sortOrder ?? 0)) + 1
}

const findMenuById = (menuId: number): MenuItem | null => {
  for (const main of mainMenus.value) {
    if (main.id === menuId) {
      return main
    }

    const child = main.children?.find((sub) => sub.id === menuId)

    if (child) {
      return child
    }
  }

  return null
}

const loadTree = async (site: MenuSite): Promise<void> => {
  try {
    await menuStore.fetchMenuTree(site)
  } catch (error) {
    message.error(getApiErrorMessage(error, '메뉴 정보를 불러오지 못했습니다.'))
  }
}

const resetEditing = (): void => {
  editTarget.value = null
  form.value = createEmptyForm()
  iconKeyword.value = ''
}

const fillForm = (menu: MenuItem): void => {
  form.value = {
    name: menu.name,
    type: menu.type,
    path: menu.path ?? '',
    sortOrder: menu.sortOrder,
    icon: menu.icon,
  }
  iconKeyword.value = ''
}

const changeSite = async (site: MenuSite): Promise<void> => {
  if (activeSite.value === site) {
    return
  }

  activeSite.value = site
  selectedMainId.value = null
  resetEditing()

  await loadTree(site)
}

const selectMain = (menu: MenuItem): void => {
  selectedMainId.value = menu.id
  editTarget.value = { level: 'MAIN', menuId: menu.id }
  fillForm(menu)
}

const selectSub = (menu: MenuItem): void => {
  editTarget.value = { level: 'SUB', menuId: menu.id }
  fillForm(menu)
}

const startCreateMain = (): void => {
  /* 신규 메인메뉴는 아직 id가 없어 서브를 붙일 수 없다. 선택을 비워 서브 컬럼의 안내를 노출한다. */
  selectedMainId.value = null
  editTarget.value = { level: 'MAIN', menuId: null }
  form.value = { ...createEmptyForm(), sortOrder: nextSortOrder(mainMenus.value) }
  iconKeyword.value = ''
}

const startCreateSub = (): void => {
  if (selectedMainId.value === null) {
    return
  }

  editTarget.value = { level: 'SUB', menuId: null }
  form.value = { ...createEmptyForm(), sortOrder: nextSortOrder(subMenus.value) }
  iconKeyword.value = ''
}

const selectIcon = (iconName: string): void => {
  form.value.icon = form.value.icon === iconName ? null : iconName
}

/*
 * MenuService의 서버 검증과 같은 규칙이다. 서버가 단일 출처이고, 여기서는 왕복을 줄이려고 먼저 거른다.
 */
const validateForm = (level: EditLevel): string | null => {
  if (!form.value.name.trim()) {
    return '메뉴명을 입력하세요.'
  }

  const path = form.value.path.trim()

  if (level === 'SUB' && !path) {
    return '서브메뉴에는 경로가 필요합니다.'
  }

  if (path && form.value.type === 'ROUTE' && !path.startsWith('/')) {
    return "ROUTE 타입의 경로는 '/'로 시작해야 합니다."
  }

  if (path && form.value.type === 'URL' && !/^https?:\/\//.test(path)) {
    return 'URL 타입의 경로는 http:// 또는 https:// 로 시작해야 합니다.'
  }

  return null
}

const save = async (): Promise<void> => {
  const target = editTarget.value

  if (!target || saving.value) {
    return
  }

  const validationMessage = validateForm(target.level)

  if (validationMessage) {
    message.warning(validationMessage)
    return
  }

  const path = form.value.path.trim()

  const request: MenuSaveRequest = {
    site: activeSite.value,
    type: form.value.type,
    parentId: target.level === 'SUB' ? selectedMainId.value : null,
    name: form.value.name.trim(),
    path: path || null,
    sortOrder: form.value.sortOrder,
    /*
     * 아이콘 피커는 관리자 서브메뉴에서만 보이지만 값은 항상 폼에 담긴 것을 그대로 보낸다.
     * 피커가 숨겨진 화면에서 null로 덮어쓰면 이미 저장된 아이콘이 지워진다.
     */
    icon: form.value.icon,
  }

  saving.value = true

  try {
    const response =
      target.menuId === null
        ? await menuApi.createMenu(request)
        : await menuApi.updateMenu(target.menuId, request)

    const savedId = response.data.data.id

    await loadTree(activeSite.value)

    if (target.level === 'MAIN') {
      selectedMainId.value = savedId
    }

    editTarget.value = { level: target.level, menuId: savedId }

    const savedMenu = findMenuById(savedId)

    if (savedMenu) {
      fillForm(savedMenu)
    }

    message.success(target.menuId === null ? '메뉴를 등록했습니다.' : '메뉴를 저장했습니다.')
  } catch (error) {
    message.error(getApiErrorMessage(error, '메뉴를 저장하지 못했습니다.'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadTree(activeSite.value)
})
</script>

<template>
  <div class="menu-manage">
    <header class="page-header">
      <h2 class="page-title">메뉴 관리</h2>
      <p class="page-description">메인메뉴를 고르면 하위 서브메뉴가 이어서 열립니다.</p>
    </header>

    <nav class="site-tabs" role="tablist" aria-label="메뉴 사이트 구분">
      <button
        v-for="tab in SITE_TABS"
        :key="tab.key"
        type="button"
        role="tab"
        class="site-tab"
        :class="{ active: activeSite === tab.key }"
        :aria-selected="activeSite === tab.key"
        @click="changeSite(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="menu-columns">
      <section class="menu-column main-column">
        <div class="column-head">
          <span class="column-title">메인메뉴</span>
          <span class="column-count">{{ mainMenus.length }}</span>
          <button
            type="button"
            class="add-button"
            title="메인메뉴 신규 등록"
            aria-label="메인메뉴 신규 등록"
            @click="startCreateMain"
          >
            <PlusOutlined />
          </button>
        </div>

        <div class="column-body">
          <button
            v-for="menu in mainMenus"
            :key="menu.id"
            type="button"
            class="row-item"
            :class="{ active: menu.id === selectedMainId }"
            @click="selectMain(menu)"
          >
            <span class="row-name">{{ menu.name }}</span>
            <span class="row-count">{{ menu.children?.length ?? 0 }}</span>
            <RightOutlined class="row-chevron" />
          </button>

          <div v-if="isCreatingMain" class="row-draft">
            <PlusOutlined />
            <span class="row-name">{{ form.name || '새 메인메뉴' }}</span>
            <span class="draft-badge">작성 중</span>
          </div>

          <p v-if="!mainMenus.length && !isCreatingMain" class="column-empty">
            등록된 메인메뉴가 없습니다.
          </p>
        </div>
      </section>

      <section class="menu-column sub-column">
        <div class="column-head">
          <span class="column-title">서브메뉴</span>
          <span class="column-subject">
            {{ selectedMain ? selectedMain.name : '메인메뉴를 선택하세요' }}
          </span>
          <button
            type="button"
            class="add-button"
            :class="{ disabled: !canAddSub }"
            :disabled="!canAddSub"
            :title="canAddSub ? '서브메뉴 신규 등록' : '메인메뉴를 먼저 선택하세요'"
            aria-label="서브메뉴 신규 등록"
            @click="startCreateSub"
          >
            <PlusOutlined />
          </button>
        </div>

        <div class="column-body">
          <button
            v-for="menu in subMenus"
            :key="menu.id"
            type="button"
            class="row-item"
            :class="{ active: menu.id === editingSubId }"
            @click="selectSub(menu)"
          >
            <span class="row-icon">
              <component :is="resolveAntIconOrFallback(menu.icon)" />
            </span>
            <span class="row-name">{{ menu.name }}</span>
            <span class="row-path">{{ menu.path ?? '—' }}</span>
          </button>

          <div v-if="isCreatingSub" class="row-draft">
            <PlusOutlined />
            <span class="row-name">{{ form.name || '새 서브메뉴' }}</span>
            <span class="draft-badge">작성 중</span>
          </div>

          <p v-if="!subMenus.length && !isCreatingSub" class="column-empty">
            등록된 서브메뉴가 없습니다.<br />메인메뉴가 저장된 뒤에 추가할 수 있습니다.
          </p>
        </div>
      </section>

      <section class="menu-column detail-column">
        <template v-if="editTarget">
          <div class="column-head detail-head">
            <h3 class="detail-title">{{ formTitle }}</h3>
            <span class="detail-kind">{{ formKind }}</span>
            <span class="detail-id">ID {{ formIdText }}</span>
          </div>

          <div class="detail-body">
            <div class="field-grid">
              <label class="field">
                <span class="field-label">메뉴명</span>
                <a-input v-model:value="form.name" placeholder="메뉴명을 입력하세요" />
              </label>

              <div class="field">
                <span class="field-label">상위 메인메뉴</span>
                <span class="field-readonly">{{ parentLabel }}</span>
              </div>

              <div class="field">
                <span class="field-label">메뉴 유형</span>
                <a-radio-group v-model:value="form.type" button-style="solid">
                  <a-radio-button value="ROUTE">ROUTE</a-radio-button>
                  <a-radio-button value="URL">URL</a-radio-button>
                </a-radio-group>
              </div>

              <label class="field">
                <span class="field-label">경로</span>
                <a-input v-model:value="form.path" class="path-input" :placeholder="pathPlaceholder" />
              </label>

              <label class="field">
                <span class="field-label">정렬 순서</span>
                <a-input-number v-model:value="form.sortOrder" class="order-input" :min="0" :precision="0" />
              </label>
            </div>

            <div v-if="isIconPickerVisible" class="icon-section">
              <div class="icon-current">
                <span class="field-label">아이콘</span>
                <span class="icon-chip">
                  <span class="icon-chip-symbol">
                    <component :is="resolveAntIconOrFallback(form.icon)" />
                  </span>
                  <span class="icon-chip-name">{{ form.icon ?? '미지정' }}</span>
                </span>
                <span class="icon-note">서브메뉴에만 지정합니다 · menu.icon 에 아이콘명 저장</span>
              </div>

              <a-input
                v-model:value="iconKeyword"
                placeholder="아이콘명 검색 (예: user, file, chart)"
                allow-clear
              >
                <template #prefix>
                  <SearchOutlined />
                </template>
              </a-input>

              <div v-if="filteredIcons.length" class="icon-grid">
                <button
                  v-for="[iconName, iconComponent] in filteredIcons"
                  :key="iconName"
                  type="button"
                  class="icon-cell"
                  :class="{ active: form.icon === iconName }"
                  :title="iconName"
                  @click="selectIcon(iconName)"
                >
                  <span class="icon-cell-symbol">
                    <component :is="iconComponent" />
                  </span>
                  <span class="icon-cell-name">{{ shortIconName(iconName) }}</span>
                </button>
              </div>

              <p v-else class="icon-empty">"{{ iconKeyword }}"와 일치하는 아이콘이 없습니다.</p>
            </div>
          </div>

          <div class="detail-foot">
            <span class="foot-hint">{{ formHint }}</span>
            <a-button @click="resetEditing">취소</a-button>
            <a-button type="primary" :loading="saving" @click="save">{{ saveText }}</a-button>
          </div>
        </template>

        <p v-else class="detail-placeholder">
          좌측에서 메뉴를 선택하거나 <PlusOutlined /> 버튼으로 새 메뉴를 등록하세요.
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 관리자 콘텐츠 영역이 세로 스크롤을 갖고 있어, 3단 컬럼은 화면 높이를 채우고 각자 스크롤한다. */
.menu-manage {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  flex: none;
}

.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 800;
  color: var(--app-text-primary);
  letter-spacing: -0.02em;
}

.page-description {
  margin: 0;
  font-size: 12.5px;
  color: var(--app-text-secondary);
}

.site-tabs {
  flex: none;
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--app-border-default);
}

.site-tab {
  padding: 0 2px 9px;
  margin-bottom: -1px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--app-text-secondary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;

  &:hover {
    color: var(--app-color-primary);
  }

  &.active {
    color: var(--app-color-primary);
    font-weight: 700;
    border-bottom-color: var(--app-color-primary);
  }
}

.menu-columns {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.menu-column {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius-lg);
  background: var(--app-bg-surface);
  box-shadow: var(--app-shadow-soft);
  overflow: hidden;
}

.main-column {
  width: 250px;
  flex: none;
}

.sub-column {
  width: 290px;
  flex: none;
}

.detail-column {
  flex: 1;
  min-width: 0;
}

.column-head {
  flex: none;
  padding: 11px 13px;
  border-bottom: 1px solid var(--app-border-subtle);
  display: flex;
  align-items: center;
  gap: 7px;
}

.column-title {
  font-size: 12.5px;
  font-weight: 800;
  color: var(--app-text-primary);
}

.column-count {
  font-size: 11px;
  color: var(--app-text-muted);
}

.column-subject {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--app-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.add-button {
  margin-left: auto;
  width: 26px;
  height: 26px;
  flex: none;
  border: 0;
  border-radius: 6px;
  background: var(--app-color-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover:not(:disabled) {
    background: var(--app-color-primary-hover);
  }

  &.disabled {
    background: var(--app-bg-muted);
    border: 1px solid var(--app-border-soft);
    color: var(--app-text-disabled);
    cursor: not-allowed;
  }
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 7px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.row-item {
  flex: none;
  height: 38px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--app-border-radius);
  background: transparent;
  box-shadow: inset 0 0 0 1px transparent;
  color: var(--app-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.15s ease,
    box-shadow 0.15s ease;

  &:hover {
    background: var(--app-bg-muted);
  }

  &.active {
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
    font-weight: 700;
    box-shadow: inset 0 0 0 1px var(--app-color-primary);
  }
}

.row-icon {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-secondary);

  :deep(.anticon) {
    font-size: 15px;
    line-height: 1;
  }
}

.row-item.active .row-icon {
  color: var(--app-color-primary);
}

.row-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row-count {
  flex: none;
  font-size: 10.5px;
  color: var(--app-text-muted);
}

.row-chevron {
  flex: none;
  font-size: 12px;
  color: var(--app-text-muted);
}

.row-path {
  flex: none;
  max-width: 96px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10.5px;
  font-weight: 400;
  color: var(--app-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row-draft {
  flex: none;
  height: 38px;
  padding: 0 10px;
  border: 1px dashed var(--app-color-primary);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-selected);
  color: var(--app-color-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
}

.draft-badge {
  flex: none;
  padding: 1px 6px;
  border: 1px solid #cfe0c8;
  border-radius: 999px;
  background: var(--app-bg-surface);
  font-size: 9.5px;
  font-weight: 700;
}

.column-empty {
  margin: auto;
  padding: 20px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
  text-align: center;
}

.detail-head {
  padding: 12px 18px;
  gap: 9px;
}

.detail-title {
  margin: 0;
  font-size: 14.5px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.detail-kind {
  padding: 2px 8px;
  border: 1px solid #cfe0c8;
  border-radius: 999px;
  background: var(--app-bg-selected);
  color: var(--app-color-primary);
  font-size: 11px;
  font-weight: 700;
}

.detail-id {
  margin-left: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: var(--app-text-muted);
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: #4b5563;
}

.field-readonly {
  height: var(--app-control-height);
  padding: 0 10px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-border-radius-sm);
  background: var(--app-bg-muted);
  color: var(--app-text-secondary);
  display: flex;
  align-items: center;
  font-size: 13px;
}

.path-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
}

.order-input {
  width: 120px;
}

.icon-section {
  padding-top: 12px;
  border-top: 1px dashed #e8ebee;
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.icon-current {
  display: flex;
  align-items: center;
  gap: 9px;
}

.icon-chip {
  padding: 4px 10px 4px 4px;
  border: 1px solid #cfe0c8;
  border-radius: 999px;
  background: var(--app-bg-selected);
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.icon-chip-symbol {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--app-bg-surface);
  color: var(--app-color-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;

  :deep(.anticon) {
    font-size: 14px;
    line-height: 1;
  }
}

.icon-chip-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--app-color-primary);
}

.icon-note {
  margin-left: auto;
  font-size: 11px;
  color: var(--app-text-muted);
}

.icon-grid {
  max-height: 210px;
  overflow-y: auto;
  padding: 2px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
  gap: 6px;
}

.icon-cell {
  height: 52px;
  border: 1px solid var(--app-border-default);
  border-radius: var(--app-border-radius);
  background: var(--app-bg-surface);
  color: var(--app-text-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-family: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;

  &:hover {
    border-color: var(--app-color-primary);
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
  }

  &.active {
    border-color: var(--app-color-primary);
    background: var(--app-bg-selected);
    color: var(--app-color-primary);
    box-shadow: inset 0 0 0 1px var(--app-color-primary);
  }

  :deep(.anticon) {
    font-size: 19px;
    line-height: 1;
  }
}

.icon-cell-name {
  max-width: 92%;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8.5px;
  color: var(--app-text-muted);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.icon-empty {
  margin: 0;
  padding: 24px 0;
  font-size: 12px;
  color: var(--app-text-muted);
  text-align: center;
}

.detail-foot {
  flex: none;
  padding: 12px 18px;
  border-top: 1px solid var(--app-border-subtle);
  display: flex;
  align-items: center;
  gap: 8px;
}

.foot-hint {
  margin-right: auto;
  font-size: 11.5px;
  color: var(--app-text-muted);
}

.detail-placeholder {
  margin: auto;
  padding: 24px;
  font-size: 13px;
  color: var(--app-text-muted);
  text-align: center;
}
</style>
