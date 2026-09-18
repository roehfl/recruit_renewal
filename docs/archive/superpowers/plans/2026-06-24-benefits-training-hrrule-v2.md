# 복리후생·교육·평가 화면 v2 전면 교체 — 구현 계획서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채용 사이트의 복리후생·교육·평가 3개 안내 화면을 목업 기준의 새 디자인(탭 + 카테고리 칩 + 카드형 리스트)으로 교체하되, 공통 컴포넌트 1개로 중복을 제거한다.

**Architecture:** `ApplicantInfoTabPanel.vue` 공통 프레젠테이션 컴포넌트가 breadcrumb·제목·설명·탭·칩·카드리스트와 모든 스타일을 소유하고, 3개 페이지 파일은 `title`/`subtitle`/`tabs` 데이터만 props로 넘기는 얇은 래퍼가 된다. 데이터 모델은 `PanelTab → Chip → CardItem`으로 3화면 통일.

**Tech Stack:** Vue 3 (`<script setup lang="ts">`), TypeScript, Vite, Vitest + @vue/test-utils(jsdom), SCSS 전역 변수(`variables.scss`). AntD 미사용(기존 3화면과 동일하게 커스텀 HTML/CSS).

**설계서:** `docs/superpowers/specs/2026-06-24-benefits-training-hrrule-v2-design.md`
**작업 레포:** `recruit_front/` (자체 git). 모든 경로는 `recruit_front/` 기준.

---

## 파일 구조

| 파일 | 책임 | 작업 |
|----|----|----|
| `src/styles/variables.scss` | 전역 디자인 토큰 | 수정: 액센트 색 5종 추가 |
| `src/views/applicant/infoTabPanel.ts` | 공유 타입(CardItem/Chip/PanelTab) | 생성 |
| `src/views/applicant/ApplicantInfoTabPanel.vue` | 탭+칩+카드 리스트 + 스타일 전담 공통 컴포넌트 | 생성 |
| `src/views/applicant/__tests__/ApplicantInfoTabPanel.spec.ts` | 공통 컴포넌트 동작 테스트 | 생성 |
| `src/views/applicant/ApplicantBenefits.vue` | 복리후생 데이터 래퍼 | 전면 재작성 |
| `src/views/applicant/__tests__/ApplicantBenefits.spec.ts` | 복리후생 카테고리 매핑 테스트 | 생성 |
| `src/views/applicant/ApplicantTraining.vue` | 교육 데이터 래퍼 | 전면 재작성 |
| `src/views/applicant/ApplicantHrRule.vue` | 평가 데이터 래퍼 | 전면 재작성 |

라우터(`src/routes/applicantRoutes.ts`)는 동일 파일명을 import하므로 **수정 없음**.

**검증 규약(프로젝트 기준):** 1차 게이트는 `npm run type-check` + `npm run test:unit`. 데이터 래퍼(교육·평가)는 단순 전사라 별도 단위테스트 없이 type-check + 공통 컴포넌트 테스트로 커버(CLAUDE.md §5: 프론트 단위테스트는 필요 시에만). 로직이 있는 공통 컴포넌트와 복리후생 카테고리 매핑에만 테스트를 둔다.

---

## Task 1: 액센트 색 토큰 추가

**Files:**
- Modify: `src/styles/variables.scss` (`--tap-*` 블록 끝, 현재 76~78행 부근)

- [ ] **Step 1: `--tap-*` 블록에 토큰 5종 추가**

`variables.scss`에서 기존 `--tap-panel-shadow: rgba(31, 41, 55, 0.06);` 줄 **바로 아래**(닫는 `}` 직전)에 추가. 기존 값은 수정하지 않는다.

```scss
  --tap-panel-shadow: rgba(31, 41, 55, 0.06);

  /* 안내 화면(복리후생/교육/평가) 카드·칩 액센트 */
  --tap-accent-bar: #cfe0c8;    /* 카드 좌측 강조바 */
  --tap-card-divider: #eef1ee;  /* 카드 하단 구분선 */
  --tap-chip-border: #dfe5dc;   /* 비활성 칩 테두리 */
  --tap-chip-text: #5b6b5f;     /* 비활성 칩 글씨 */
  --tap-chip-count: #9aa896;    /* 비활성 칩 개수 배지 */
}
```

- [ ] **Step 2: type-check로 회귀 없음 확인**

Run: `npm run type-check`
Expected: PASS (SCSS 변수 추가는 타입에 영향 없음. 에러 0)

- [ ] **Step 3: Commit**

```bash
git add src/styles/variables.scss
git commit -m "feat(styles): 안내 화면 카드·칩 액센트 토큰 추가"
```

---

## Task 2: 공유 타입 모듈 생성

**Files:**
- Create: `src/views/applicant/infoTabPanel.ts`

- [ ] **Step 1: 타입 파일 작성**

```ts
// 복리후생/교육/평가 안내 화면 공통 데이터 모델

export interface CardItem {
  label: string
  desc?: string
}

export interface Chip {
  key: string
  label: string
  items: CardItem[]
}

export interface PanelTab {
  key: string
  label: string
  /** chips[0]이 기본 선택('전체') */
  chips: Chip[]
}
```

- [ ] **Step 2: type-check 통과 확인**

Run: `npm run type-check`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/views/applicant/infoTabPanel.ts
git commit -m "feat(applicant): 안내 화면 공통 타입(PanelTab/Chip/CardItem) 추가"
```

---

## Task 3: 공통 컴포넌트 ApplicantInfoTabPanel.vue (TDD)

**Files:**
- Test: `src/views/applicant/__tests__/ApplicantInfoTabPanel.spec.ts`
- Create: `src/views/applicant/ApplicantInfoTabPanel.vue`

의존: Task 1(토큰), Task 2(타입).

- [ ] **Step 1: 실패하는 테스트 작성**

`src/views/applicant/__tests__/ApplicantInfoTabPanel.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantInfoTabPanel from '../ApplicantInfoTabPanel.vue'
import type { PanelTab } from '../infoTabPanel'

const tabs: PanelTab[] = [
  {
    key: 'a',
    label: 'AAA',
    chips: [
      {
        key: 'all',
        label: '전체',
        items: Array.from({ length: 10 }, (_, i) => ({ label: `L${i}`, desc: `D${i}` })),
      },
      { key: 'sub', label: '서브', items: [{ label: 'only', desc: 'od' }] },
    ],
  },
  {
    key: 'b',
    label: 'BBB',
    chips: [{ key: 'all', label: '전체', items: [{ label: 'b1' }, { label: 'b2', desc: 'bd2' }] }],
  },
]

const factory = () =>
  mount(ApplicantInfoTabPanel, {
    props: { title: '제목', subtitle: '설명', tabs },
    global: { stubs: { ApplicantBreadcrumb: true } },
  })

describe('ApplicantInfoTabPanel', () => {
  it('제목과 설명을 렌더한다', () => {
    const w = factory()
    expect(w.text()).toContain('제목')
    expect(w.text()).toContain('설명')
  })

  it('기본으로 첫 탭이 활성이고 첫 칩 항목을 보여준다', () => {
    const w = factory()
    expect(w.find('#tab-a').classes()).toContain('active')
    expect(w.find('#tab-b').classes()).not.toContain('active')
    expect(w.findAll('.card')).toHaveLength(10)
  })

  it('항목이 7개 초과면 2열 클래스가 붙는다', () => {
    const w = factory()
    expect(w.find('.card-list').classes()).toContain('two-col')
  })

  it('칩을 클릭하면 해당 칩 항목으로 필터링된다', async () => {
    const w = factory()
    const chips = w.findAll('.chip')
    expect(chips).toHaveLength(2)
    await chips[1].trigger('click') // 서브
    expect(w.findAll('.card')).toHaveLength(1)
    expect(w.text()).toContain('only')
    expect(w.find('.card-list').classes()).not.toContain('two-col')
  })

  it('탭을 바꾸면 항목이 바뀌고 칩이 첫 칩으로 리셋된다', async () => {
    const w = factory()
    await w.findAll('.chip')[1].trigger('click') // A의 서브 → 1개
    expect(w.findAll('.card')).toHaveLength(1)

    await w.find('#tab-b').trigger('click') // B 탭
    expect(w.find('#tab-b').classes()).toContain('active')
    expect(w.findAll('.card')).toHaveLength(2)

    await w.find('#tab-a').trigger('click') // 다시 A → 칩 리셋되어 전체(10)
    expect(w.findAll('.card')).toHaveLength(10)
  })

  it('desc가 없으면 설명 노드를 렌더하지 않는다', async () => {
    const w = factory()
    await w.find('#tab-b').trigger('click') // b1(설명없음), b2(설명있음)
    const cards = w.findAll('.card')
    expect(cards[0].find('.card-desc').exists()).toBe(false) // b1
    expect(cards[1].find('.card-desc').exists()).toBe(true) // b2
  })
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `npm run test:unit -- --run ApplicantInfoTabPanel`
Expected: FAIL — `Failed to resolve import "../ApplicantInfoTabPanel.vue"` (컴포넌트 미존재)

- [ ] **Step 3: 공통 컴포넌트 구현**

`src/views/applicant/ApplicantInfoTabPanel.vue`:

```vue
<template>
  <section class="info-tab-page">
    <div class="page-inner">
      <ApplicantBreadcrumb />

      <h1 class="page-title" :class="{ lg: largeTitle }">{{ title }}</h1>
      <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>

      <!-- 탭 -->
      <div class="tab-row" role="tablist">
        <button
          v-for="tab in tabs"
          :id="`tab-${tab.key}`"
          :key="tab.key"
          type="button"
          class="tab-btn"
          :class="{ active: tab.key === activeTabKey }"
          role="tab"
          :aria-selected="tab.key === activeTabKey"
          :aria-controls="`panel-${tab.key}`"
          @click="selectTab(tab.key)"
        >
          {{ tab.label }}
        </button>
        <div class="tab-fill" aria-hidden="true"></div>
      </div>

      <!-- 패널 -->
      <div
        :id="`panel-${activeTabKey}`"
        class="panel"
        role="tabpanel"
        :aria-labelledby="`tab-${activeTabKey}`"
      >
        <div class="panel-inner">
          <!-- 칩 -->
          <div class="chip-row">
            <button
              v-for="chip in activeTab.chips"
              :key="chip.key"
              type="button"
              class="chip"
              :class="{ active: chip.key === activeChipKey }"
              @click="selectChip(chip.key)"
            >
              {{ chip.label }}
              <span class="chip-count">{{ chip.items.length }}</span>
            </button>
          </div>

          <!-- 카드 리스트 -->
          <div class="card-list" :class="{ 'two-col': activeItems.length > 7 }">
            <div v-for="(item, i) in activeItems" :key="i" class="card">
              <div class="card-bar" aria-hidden="true"></div>
              <div class="card-body">
                <div class="card-label">{{ item.label }}</div>
                <div v-if="item.desc" class="card-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

const props = defineProps<{
  title: string
  subtitle?: string
  largeTitle?: boolean
  tabs: PanelTab[]
}>()

const activeTabKey = ref<string>(props.tabs[0]?.key ?? '')

const activeTab = computed<PanelTab>(
  () => props.tabs.find((t) => t.key === activeTabKey.value) ?? props.tabs[0],
)

const activeChipKey = ref<string>(props.tabs[0]?.chips[0]?.key ?? '')

const activeChip = computed(
  () =>
    activeTab.value.chips.find((c) => c.key === activeChipKey.value) ?? activeTab.value.chips[0],
)

const activeItems = computed<CardItem[]>(() => activeChip.value?.items ?? [])

const selectTab = (key: string): void => {
  activeTabKey.value = key
  const next = props.tabs.find((t) => t.key === key)
  activeChipKey.value = next?.chips[0]?.key ?? '' // 탭 변경 시 첫 칩으로 리셋
}

const selectChip = (key: string): void => {
  activeChipKey.value = key
}
</script>

<style scoped>
.info-tab-page {
  width: 100%;
  background: #ffffff;
  color: var(--tap-text);
}

.page-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 42px 20px 80px;
}

.page-title {
  margin: 18px 0 0;
  font-size: 38px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.04em;
  color: var(--tap-text);
}

.page-title.lg {
  font-size: 40px;
}

.page-subtitle {
  margin: 9px 0 0;
  font-size: 15px;
  line-height: 1.6;
  color: var(--app-text-muted);
  letter-spacing: -0.02em;
}

/* 탭 */
.tab-row {
  display: flex;
  align-items: flex-end;
  margin-top: 34px;
}

.tab-btn {
  appearance: none;
  -webkit-appearance: none;
  box-sizing: border-box;
  min-width: 230px;
  height: 58px;
  padding: 0 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  letter-spacing: -0.02em;
  color: var(--app-text-muted);
  font-weight: 500;
  border: 0;
  border-bottom: 2px solid var(--app-color-primary-hover);
  border-radius: 8px 8px 0 0;
  background: var(--tap-muted-soft);
  cursor: pointer;
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}

.tab-btn:hover {
  color: var(--app-color-primary);
}

.tab-btn.active {
  color: var(--app-color-primary-hover);
  font-weight: 700;
  border: 2px solid var(--app-color-primary-hover);
  border-bottom: 0;
  background: #ffffff;
}

.tab-fill {
  flex: 1;
  min-width: 0;
  height: 58px;
  border-bottom: 2px solid var(--app-color-primary-hover);
}

/* 패널 */
.panel {
  border: 2px solid var(--app-color-primary-hover);
  border-top: 0;
  border-radius: 0 0 8px 8px;
  background: #ffffff;
  box-shadow: 0 10px 28px var(--tap-panel-shadow);
}

.panel-inner {
  padding: 34px 44px 42px;
}

/* 칩 */
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 26px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 15px;
  border-radius: 20px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--tap-chip-text);
  background: #ffffff;
  border: 1px solid var(--tap-chip-border);
  cursor: pointer;
  transition: all 0.15s ease;
}

.chip.active {
  background: var(--app-color-primary);
  color: #ffffff;
  border-color: var(--app-color-primary);
}

.chip-count {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--tap-chip-count);
}

.chip.active .chip-count {
  color: rgba(255, 255, 255, 0.7);
}

/* 카드 리스트 */
.card-list {
  column-count: 1;
  column-gap: 30px;
}

.card-list.two-col {
  column-count: 2;
}

.card {
  display: flex;
  gap: 13px;
  break-inside: avoid;
  padding: 14px 4px;
  border-bottom: 1px solid var(--tap-card-divider);
}

.card-bar {
  flex-shrink: 0;
  width: 3px;
  align-self: stretch;
  background: var(--tap-accent-bar);
  border-radius: 2px;
}

.card-label {
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text-primary);
  letter-spacing: -0.01em;
}

.card-desc {
  margin-top: 5px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);
  letter-spacing: -0.02em;
  word-break: keep-all;
  text-wrap: pretty;
}

/* 반응형 */
@media (max-width: 768px) {
  .page-inner {
    padding: 32px 16px 64px;
  }

  .page-title,
  .page-title.lg {
    font-size: 30px;
  }

  .tab-row {
    margin-top: 28px;
  }

  .tab-fill {
    display: none;
  }

  .tab-btn {
    flex: 1;
    min-width: 0;
    height: 52px;
    padding: 0 12px;
    font-size: 14px;
  }

  .panel-inner {
    padding: 24px 18px 30px;
  }

  .card-list.two-col {
    column-count: 1;
  }
}
</style>
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `npm run test:unit -- --run ApplicantInfoTabPanel`
Expected: PASS (6개 it 모두 통과)

- [ ] **Step 5: type-check 통과 확인**

Run: `npm run type-check`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/views/applicant/ApplicantInfoTabPanel.vue src/views/applicant/__tests__/ApplicantInfoTabPanel.spec.ts
git commit -m "feat(applicant): 안내 화면 공통 컴포넌트 ApplicantInfoTabPanel 추가"
```

---

## Task 4: ApplicantBenefits.vue 재작성 (카테고리 매핑 테스트 포함)

**Files:**
- Test: `src/views/applicant/__tests__/ApplicantBenefits.spec.ts`
- Modify(전면 재작성): `src/views/applicant/ApplicantBenefits.vue`

의존: Task 3.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/views/applicant/__tests__/ApplicantBenefits.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantBenefits from '../ApplicantBenefits.vue'
import ApplicantInfoTabPanel from '../ApplicantInfoTabPanel.vue'
import type { PanelTab } from '../infoTabPanel'

describe('ApplicantBenefits', () => {
  it('복리후생 카테고리 칩과 항목 매핑이 목업과 일치한다', () => {
    const w = mount(ApplicantBenefits, {
      global: { stubs: { ApplicantBreadcrumb: true } },
    })
    const tabs = w.findComponent(ApplicantInfoTabPanel).props('tabs') as PanelTab[]

    expect(tabs.map((t) => t.label)).toEqual(['복리 후생 제도', '휴가 제도'])

    const benefit = tabs.find((t) => t.key === 'benefit')!
    expect(benefit.chips.map((c) => c.label)).toEqual([
      '전체',
      '경제적 지원',
      '건강과 의료',
      '여가와 문화',
      '성장과 교육',
    ])
    expect(benefit.chips[0].items).toHaveLength(14)
    expect(benefit.chips.find((c) => c.key === 'econ')!.items.map((i) => i.label)).toEqual([
      '경조사',
      '주택자금대출',
      '선택적 복리후생',
      '명절',
    ])
    expect(benefit.chips.find((c) => c.key === 'growth')!.items.map((i) => i.label)).toEqual([
      '학자금',
      '교육비 지원',
    ])

    const leave = tabs.find((t) => t.key === 'leave')!
    expect(leave.chips).toHaveLength(1)
    expect(leave.chips[0].items).toHaveLength(4)
  })
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `npm run test:unit -- --run ApplicantBenefits`
Expected: FAIL — 기존 `ApplicantBenefits.vue`에는 `tabs` prop을 넘기는 `ApplicantInfoTabPanel`이 없어 `findComponent`가 비어 있음 → `props('tabs')`가 undefined라 단언 실패

- [ ] **Step 3: ApplicantBenefits.vue 전면 재작성**

기존 `<template>/<script>/<style>` 전체를 아래로 교체:

```vue
<template>
  <ApplicantInfoTabPanel
    title="복리 후생 제도"
    subtitle="임직원의 안정적인 생활과 성장을 위한 다양한 지원 제도를 안내합니다."
    large-title
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

// 인덱스 0~13 — 카테고리 칩이 인덱스로 그룹을 참조한다(중복 desc 방지).
const benefits: CardItem[] = [
  { label: '경조사', desc: '경조금(결혼·사망, 본인 및 배우자 부모 칠순 등)과 화환·조화 지원' }, // 0
  { label: '학자금', desc: '유치원·중·고등학교, 대학교 학자금 지급' }, // 1
  { label: '의료비', desc: '본인 및 건강보험증 등재 가족에게 연간 1,000만원 한도 지원' }, // 2
  { label: '치과비', desc: '본인이 사용한 치과진료비를 5년간 100만원 한도 지원' }, // 3
  { label: '동호회비', desc: '축구·독서 등 사내 동호회 활동 경비 지원' }, // 4
  { label: '안식휴가비', desc: '5년 100만원, 10년 200만원, 15년 이상 매 5년 400만원' }, // 5
  { label: '주택자금대출', desc: '직원 전세자금 및 주택 구입자금 대출 지원' }, // 6
  { label: '선택적 복리후생', desc: '개인별 부여 포인트 내에서 여가활동 등 비용 지원' }, // 7
  { label: '건강검진', desc: '격년 주기로 종합건강검진 실시' }, // 8
  { label: '명절', desc: '경로효친비 및 명절 선물 지원' }, // 9
  { label: '콘도 이용', desc: '전국 각지의 회사 보유 콘도 이용 지원' }, // 10
  { label: '기타 복리후생', desc: '가을행사 기념품, 장기근속자 포상 등 지원' }, // 11
  { label: '교육비 지원', desc: '직무관련 교육 이수 및 자격증 취득 경비 지원' }, // 12
  { label: '단체상해보험', desc: '임직원 단체상해보험 가입 지원' }, // 13
]

const leaves: CardItem[] = [
  { label: '정기휴가', desc: '유급휴가 연 5일' },
  { label: '법정휴가', desc: '근로기준법에 따른 연차휴가' },
  { label: '경조휴가·공가', desc: '각종 경조사 휴가 및 공가' },
  { label: '안식휴가', desc: '5년 4일, 10년 7일, 15년 이상 매 5년 10일' },
]

const pick = (...idx: number[]): CardItem[] => idx.map((i) => benefits[i])

const tabs: PanelTab[] = [
  {
    key: 'benefit',
    label: '복리 후생 제도',
    chips: [
      { key: 'all', label: '전체', items: benefits },
      { key: 'econ', label: '경제적 지원', items: pick(0, 6, 7, 9) },
      { key: 'health', label: '건강과 의료', items: pick(2, 3, 8, 13) },
      { key: 'leisure', label: '여가와 문화', items: pick(4, 10, 5, 11) },
      { key: 'growth', label: '성장과 교육', items: pick(1, 12) },
    ],
  },
  {
    key: 'leave',
    label: '휴가 제도',
    chips: [{ key: 'all', label: '전체', items: leaves }],
  },
]
</script>
```

> 래퍼에는 `<style>` 블록을 두지 않는다(스타일은 공통 컴포넌트 소유).

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `npm run test:unit -- --run ApplicantBenefits`
Expected: PASS

- [ ] **Step 5: type-check 통과 확인**

Run: `npm run type-check`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/views/applicant/ApplicantBenefits.vue src/views/applicant/__tests__/ApplicantBenefits.spec.ts
git commit -m "refactor(applicant): 복리후생 화면 v2 디자인으로 교체"
```

---

## Task 5: ApplicantTraining.vue 재작성

**Files:**
- Modify(전면 재작성): `src/views/applicant/ApplicantTraining.vue`

의존: Task 3.

- [ ] **Step 1: ApplicantTraining.vue 전면 재작성**

기존 `<template>/<script>/<style>` 전체를 아래로 교체:

```vue
<template>
  <ApplicantInfoTabPanel
    title="교육 제도"
    subtitle="임직원의 전문성과 성장을 지원하는 사내·외 교육 과정을 안내합니다."
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

const internal: CardItem[] = [
  { label: '직무교육', desc: '자산관리전문가 · 고객업무전문가 · 실무교육' },
  {
    label: '직급교육',
    desc: '신입사원 입문교육 · 수시입사자 안내 · Follow-up 과정 · 승진 자격과정 · 리더십과정',
  },
  { label: '자격관리교육', desc: '자격시험 대비과정 · 투자권유자문인력 투자자보호' },
  { label: 'ADVANCED 교육', desc: '고급관계관리(ARM) · 고급정보기술(AIT)' },
]

const external: CardItem[] = [
  { label: '금융투자교육원 주관 교육', desc: '전문교육 · 집합교육 · 온라인교육' },
  {
    label: '기타 외부기관 주관 교육',
    desc: '영업 · 운용 · IB · 리서치 · IT · 지원 · 준법/리스크/정보보호 · 공통',
  },
  { label: '교육 신청절차', desc: '금융투자교육원 교육 신청 · 기타 외부기관 주관 교육 신청' },
]

const mandatory: CardItem[] = [
  { label: '직장 내 성희롱 예방교육', desc: '근거: 남녀고용평등과 일·가정 양립 지원에 관한 법률' },
  { label: '금융정보보호교육', desc: '근거: 전자금융감독규정 제19조 2항' },
  {
    label: '금융투자전문인력 보수교육',
    desc: '근거: 금융투자전문인력과 자격시험에 관한 규정 제5-2조 4항',
  },
]

const tabs: PanelTab[] = [
  { key: 'internal', label: '사내교육', chips: [{ key: 'all', label: '전체', items: internal }] },
  { key: 'external', label: '사외교육', chips: [{ key: 'all', label: '전체', items: external }] },
  { key: 'mandatory', label: '법정의무교육', chips: [{ key: 'all', label: '전체', items: mandatory }] },
]
</script>
```

- [ ] **Step 2: type-check 통과 확인**

Run: `npm run type-check`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/views/applicant/ApplicantTraining.vue
git commit -m "refactor(applicant): 교육 제도 화면 v2 디자인으로 교체"
```

---

## Task 6: ApplicantHrRule.vue 재작성

**Files:**
- Modify(전면 재작성): `src/views/applicant/ApplicantHrRule.vue`

의존: Task 3.

- [ ] **Step 1: ApplicantHrRule.vue 전면 재작성**

기존 `<template>/<script>/<style>` 전체를 아래로 교체:

```vue
<template>
  <ApplicantInfoTabPanel
    title="보상 및 평가"
    subtitle="역량과 성과에 기반한 보상 체계와 공정한 평가 제도를 안내합니다."
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import ApplicantInfoTabPanel from '@/views/applicant/ApplicantInfoTabPanel.vue'
import type { CardItem, PanelTab } from '@/views/applicant/infoTabPanel'

const reward: CardItem[] = [
  { label: '연봉제 시행', desc: '직원 개개인의 역량과 성과에 근거한 연봉제 시행' },
  { label: '성과급 지급', desc: '직원 개개인의 종합평가 결과에 근거한 성과급 지급' },
]

const review: CardItem[] = [
  {
    label: '평가그룹별 평가 시행',
    desc: 'Assistant: 신입사원에 한해 입사 후 일정기간 Career path 탐색 기회 부여 · Manager: 역량 및 성과에 따른 종합평가',
  },
  {
    label: '평가방법',
    desc: '역량평가: 평가그룹별 assign 된 역량 평가 · 성과평가: 설정 목표 대비 달성도 평가 · 종합평가: 역량·성과를 종합해 승진·전보·연봉을 합리적으로 결정',
  },
]

const tabs: PanelTab[] = [
  { key: 'reward', label: '보상제도', chips: [{ key: 'all', label: '전체', items: reward }] },
  { key: 'review', label: '평가제도', chips: [{ key: 'all', label: '전체', items: review }] },
]
</script>
```

- [ ] **Step 2: type-check 통과 확인**

Run: `npm run type-check`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/views/applicant/ApplicantHrRule.vue
git commit -m "refactor(applicant): 보상 및 평가 화면 v2 디자인으로 교체"
```

---

## Task 7: 전체 검증

**Files:** 없음(검증만).

- [ ] **Step 1: 전체 단위 테스트**

Run: `npm run test:unit -- --run`
Expected: PASS — 신규 spec 2개(ApplicantInfoTabPanel, ApplicantBenefits) + 기존 HelloWorld 포함 전부 통과

- [ ] **Step 2: 타입 체크**

Run: `npm run type-check`
Expected: PASS (에러 0)

- [ ] **Step 3: 빌드(선택, 권장)**

Run: `npm run build`
Expected: PASS — type-check + vite build 성공. SCSS 토큰·컴포넌트 정상 컴파일

- [ ] **Step 4: 수동 시각 확인**

Run: `npm run dev` 후 브라우저에서 확인:
- `/applicant/benefits` — 제목 "복리 후생 제도" + 설명 1줄, 탭 2개, "복리 후생 제도" 탭에 칩 5개(전체/경제적 지원/건강과 의료/여가와 문화/성장과 교육), 전체 14개 항목이 **2열** 카드, 칩 클릭 시 필터링, "휴가 제도" 탭은 칩 1개·4개 항목 1열.
- `/applicant/training` — 제목 "교육 제도", 탭 3개(사내/사외/법정의무), 각 카드 1열, desc에 ` · ` 구분 항목.
- `/applicant/hrRule` — 제목 "보상 및 평가", 탭 2개(보상제도/평가제도), 각 카드 1열.
- 모바일 폭(≤768px)에서 2열→1열, 탭 균등·채움라인 숨김 확인.

> 정적 콘텐츠 화면이라 API 호출 없음 → `api-contract.md` 변경 불필요(계약 영향 없음). 백엔드 작업 없음.

---

## Self-Review (작성자 점검 완료)

**1. 스펙 커버리지**
- §2 범위(공통 컴포넌트+타입+3래퍼+토큰) → Task 1·2·3·4·5·6 ✓
- §3 컴포넌트 구조(공통이 page-inner·breadcrumb 소유, 래퍼는 데이터만) → Task 3 템플릿 + Task 4·5·6 래퍼 ✓
- §4 데이터 모델·동작(탭→칩 리셋, 열 규칙 ≤7/>7) → Task 2 타입 + Task 3 로직/테스트 ✓
- §5 화면별 데이터(복리후생 14+4카테고리, 교육 10, 평가 4, 문구 그대로) → Task 4·5·6 + Task 4 매핑 테스트 ✓
- §6 UI 명세(치수·색) → Task 3 scoped CSS ✓
- §7 토큰(재사용 + 신규 5종) → Task 1 + Task 3 CSS의 var() 참조 ✓
- §8 반응형 → Task 3 @media ✓
- §10 검증 → Task 7 ✓

**2. Placeholder 스캔:** TBD/TODO/"적절히"/"유사함" 없음. 모든 코드·명령·기대결과 실값. ✓

**3. 타입/이름 일관성:** `CardItem`/`Chip`/`PanelTab`(Task 2) ↔ 컴포넌트 import(Task 3) ↔ 래퍼 import(Task 4·5·6) 일치. 칩 key(`all`/`econ`/`health`/`leisure`/`growth`)·탭 key(`benefit`/`leave`/`internal`/`external`/`mandatory`/`reward`/`review`)·CSS 클래스(`.card`/`.card-list.two-col`/`.chip`/`#tab-*`)가 테스트 셀렉터와 정확히 대응. ✓

**4. 의도된 스펙 편차(placeholder 아님):**
- 제목 하단 padding을 80px로 통일(스펙은 평가 화면만 88px). 8px 차이는 무의미하여 단순화. largeTitle prop으로 제목 40/38px만 구분.
