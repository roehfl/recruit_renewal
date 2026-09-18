# 직무소개 모달 리디자인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지원자 직무소개 화면의 직무 상세 모달을 기준 디자인(`recruit/dist/신영증권 직무소개 모달.html`)으로 교체하고, 7개 직무 데이터를 구조화 필드로 재구조화한다(카피 100% 보존).

**Architecture:** 모달 본문을 전담하는 순수 자식 컴포넌트 `DutyIntroModalBody.vue`(props `job`, emit `close`)를 신설하고, 부모 `ApplicantDutyIntroduction.vue`는 통 HTML 문자열 데이터를 구조화 `DutyItem[]`로 교체한 뒤 `a-modal` 셸(기본 헤더/닫기 숨김) 안에 자식을 렌더한다. 타입은 `src/types/duty.ts`로 분리.

**Tech Stack:** Vue 3 `<script setup lang="ts">`, ant-design-vue (`a-modal`/`a-card`/`a-button`), Vitest 4 + @vue/test-utils 2, scoped CSS.

**작업 디렉토리:** 모든 경로는 `recruit_front/` 기준. 모든 명령·커밋은 `recruit_front/`(자체 git repo)에서 수행한다.

**설계서:** `recruit/docs/superpowers/specs/2026-06-29-duty-introduction-modal-redesign-design.md`

---

## File Structure

| 파일 | 역할 | 액션 |
|---|---|---|
| `src/types/duty.ts` | `DutyItem`/`DutySkill` 타입 정의 | Create |
| `src/views/applicant/DutyIntroModalBody.vue` | 모달 본문(헤더+3섹션+차별점) 렌더 전담 자식 | Create |
| `src/views/applicant/__tests__/DutyIntroModalBody.spec.ts` | 자식 컴포넌트 단위 테스트 | Create |
| `src/views/applicant/ApplicantDutyIntroduction.vue` | 데이터 재구조화 + a-modal 셸 + 카드 바인딩 | Modify |
| `src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts` | 부모 렌더 스모크 테스트 | Create |

---

## Task 1: DutyItem 타입 모듈

**Files:**
- Create: `src/types/duty.ts`

- [ ] **Step 1: 타입 모듈 작성**

`src/types/duty.ts`:

```ts
export interface DutySkill {
  /** 굵게 표시될 선두 문구 (예: "공감 능력 및 커뮤니케이션 역량:") */
  lead: string
  /** 이어지는 설명 */
  body: string
}

export interface DutyItem {
  /** 모달 open/close 상태 매핑 키 (기존 url 값 유지) */
  url: string
  /** 헤더 영문 라벨 (eyebrow) */
  eyebrow: string
  /** 국문 타이틀 */
  title: string
  /** 필요역량 — 전공 pill 값 */
  major: string
  /** 필요역량 — 학위 pill 값 */
  degree: string
  /** ① 직무소개 */
  intro: string
  /** ② 필요역량 항목 리스트 */
  skills: DutySkill[]
  /** ③ 커리어패스 */
  career: string
  /** 신영증권만의 차별점 */
  diff: string
}
```

- [ ] **Step 2: 타입 체크로 모듈 유효성 확인**

Run: `npm run type-check`
Expected: PASS (신규 파일이 컴파일 에러를 만들지 않음)

- [ ] **Step 3: 커밋**

```bash
git add src/types/duty.ts
git commit -m "feat(duty): DutyItem/DutySkill 타입 모듈 추가"
```

---

## Task 2: DutyIntroModalBody 자식 컴포넌트 (TDD)

**Files:**
- Create: `src/views/applicant/DutyIntroModalBody.vue`
- Test: `src/views/applicant/__tests__/DutyIntroModalBody.spec.ts`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/views/applicant/__tests__/DutyIntroModalBody.spec.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DutyIntroModalBody from '../DutyIntroModalBody.vue'
import type { DutyItem } from '@/types/duty'

const job: DutyItem = {
  url: 'Sample Url',
  eyebrow: 'Wealth Management Service',
  title: '자산관리 서비스',
  major: '전공 무관',
  degree: '학사 이상',
  intro: '직무소개 본문입니다.',
  skills: [
    { lead: '역량A:', body: '설명A' },
    { lead: '역량B:', body: '설명B' },
  ],
  career: '커리어패스 본문입니다.',
  diff: '차별점 본문입니다.',
}

const factory = () => mount(DutyIntroModalBody, { props: { job } })

describe('DutyIntroModalBody', () => {
  it('eyebrow와 국문 타이틀을 렌더한다', () => {
    const w = factory()
    expect(w.find('.dm-eyebrow-text').text()).toBe('Wealth Management Service')
    expect(w.find('.dm-title').text()).toBe('자산관리 서비스')
  })

  it('세 개의 번호 섹션과 제목을 렌더한다', () => {
    const w = factory()
    const badges = w.findAll('.dm-badge')
    expect(badges.map((b) => b.text())).toEqual(['1', '2', '3'])
    expect(w.text()).toContain('직무소개')
    expect(w.text()).toContain('필요역량')
    expect(w.text()).toContain('커리어패스')
  })

  it('전공/학위 pill과 스킬 항목을 렌더한다', () => {
    const w = factory()
    const pills = w.findAll('.dm-pill')
    expect(pills).toHaveLength(2)
    expect(pills[0]!.text()).toContain('전공')
    expect(pills[0]!.text()).toContain('전공 무관')
    expect(pills[1]!.text()).toContain('학위')
    expect(pills[1]!.text()).toContain('학사 이상')

    const skills = w.findAll('.dm-skill')
    expect(skills).toHaveLength(2)
    expect(skills[0]!.find('.dm-skill-lead').text()).toBe('역량A:')
    expect(skills[0]!.text()).toContain('설명A')
  })

  it('차별점은 번호 뱃지 없이 하이라이트 박스로 렌더한다', () => {
    const w = factory()
    const box = w.find('.dm-highlight')
    expect(box.exists()).toBe(true)
    expect(box.find('.dm-highlight-title').text()).toBe('신영증권만의 차별점')
    expect(box.text()).toContain('차별점 본문입니다.')
    // 차별점 박스 안에는 번호 뱃지가 없다
    expect(box.find('.dm-badge').exists()).toBe(false)
  })

  it('✕ 클릭 시 close 이벤트를 emit 한다', async () => {
    const w = factory()
    await w.find('.dm-close').trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `npm run test:unit -- run src/views/applicant/__tests__/DutyIntroModalBody.spec.ts`
Expected: FAIL — `Failed to resolve import ".../DutyIntroModalBody.vue"` (컴포넌트 미존재)

- [ ] **Step 3: 컴포넌트 구현**

`src/views/applicant/DutyIntroModalBody.vue`:

```vue
<template>
  <div class="dm-modal">
    <!-- header -->
    <div class="dm-header">
      <div class="dm-eyebrow">
        <span class="dm-dot"></span>
        <span class="dm-eyebrow-text">{{ job.eyebrow }}</span>
      </div>
      <h2 class="dm-title">{{ job.title }}</h2>
      <button class="dm-close" type="button" aria-label="닫기" @click="emit('close')">✕</button>
    </div>

    <!-- body -->
    <div class="dm-scroll dm-body">
      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">1</span>
          <h3 class="dm-section-title">직무소개</h3>
        </div>
        <p class="dm-text dm-indent">{{ job.intro }}</p>
      </section>

      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">2</span>
          <h3 class="dm-section-title">필요역량</h3>
        </div>
        <div class="dm-indent">
          <div class="dm-pills">
            <span class="dm-pill">전공 <span class="dm-pill-val">{{ job.major }}</span></span>
            <span class="dm-pill">학위 <span class="dm-pill-val">{{ job.degree }}</span></span>
          </div>
          <div class="dm-skills">
            <div v-for="(s, i) in job.skills" :key="i" class="dm-skill">
              <span class="dm-diamond"></span>
              <p class="dm-text"><b class="dm-skill-lead">{{ s.lead }}</b> {{ s.body }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="dm-section">
        <div class="dm-section-head">
          <span class="dm-badge">3</span>
          <h3 class="dm-section-title">커리어패스</h3>
        </div>
        <p class="dm-text dm-indent">{{ job.career }}</p>
      </section>

      <div class="dm-highlight">
        <h3 class="dm-highlight-title">신영증권만의 차별점</h3>
        <p class="dm-text">{{ job.diff }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DutyItem } from '@/types/duty'

defineProps<{ job: DutyItem }>()
const emit = defineEmits<{ (e: 'close'): void }>()
</script>

<style scoped>
.dm-modal {
  font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #1f2937;
}

.dm-header {
  position: relative;
  padding: 32px 40px 24px;
  border-bottom: 1px solid #eef1ee;
}

.dm-eyebrow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.dm-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #2f6f55;
}

.dm-eyebrow-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #2f6f55;
  text-transform: uppercase;
}

.dm-title {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1f2937;
}

.dm-close {
  position: absolute;
  top: 28px;
  right: 32px;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: #f5f7fa;
  color: #6b7280;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}

.dm-close:hover {
  background: #eaf8f3;
  color: #0f4726;
}

.dm-body {
  max-height: 560px;
  overflow-y: auto;
  padding: 28px 40px 36px;
}

.dm-section {
  margin-bottom: 26px;
}

.dm-section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.dm-badge {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #0f4726;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.dm-section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.dm-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.75;
  color: #374151;
  word-break: keep-all;
}

.dm-indent {
  margin-left: 42px;
}

.dm-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.dm-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid #dfe5dc;
  border-radius: 999px;
  background: #f4f8f0;
  font-size: 13px;
  font-weight: 600;
  color: #0f4726;
}

.dm-pill-val {
  color: #374151;
  font-weight: 500;
}

.dm-skills {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.dm-skill {
  display: flex;
  gap: 12px;
}

.dm-diamond {
  flex: none;
  margin-top: 7px;
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: #2f6f55;
  transform: rotate(45deg);
}

.dm-skill .dm-text {
  font-size: 14.5px;
  line-height: 1.7;
}

.dm-skill-lead {
  color: #0f4726;
  font-weight: 700;
}

.dm-highlight {
  padding: 22px 24px;
  border: 1px solid #e7efe2;
  border-radius: 12px;
  background: #f8faf6;
}

.dm-highlight-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 700;
  color: #0f4726;
}

.dm-scroll::-webkit-scrollbar {
  width: 12px;
}

.dm-scroll::-webkit-scrollbar-track {
  background: #f9f9f9;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb {
  border: 3px solid #f9f9f9;
  background: #bfbfbf75;
  border-radius: 10px;
}

.dm-scroll::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}
</style>
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `npm run test:unit -- run src/views/applicant/__tests__/DutyIntroModalBody.spec.ts`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/views/applicant/DutyIntroModalBody.vue src/views/applicant/__tests__/DutyIntroModalBody.spec.ts
git commit -m "feat(duty): 직무소개 모달 본문 컴포넌트 DutyIntroModalBody 추가"
```

---

## Task 3: 부모 컴포넌트 데이터 재구조화 + 모달 셸 교체

**Files:**
- Modify: `src/views/applicant/ApplicantDutyIntroduction.vue`
- Test: `src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts`

> 데이터 본문(intro/skills/career/diff)은 **기존 `ApplicantDutyIntroduction.vue`의 `description` 문자열에서 그대로 복사**한 것이다. 아래 배열의 한글 문구를 임의로 수정하지 말 것.

- [ ] **Step 1: 부모 스모크 테스트 작성**

`src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts`:

```ts
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ApplicantDutyIntroduction from '../ApplicantDutyIntroduction.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

const stubs = {
  ApplicantBreadcrumb: true,
  DutyIntroModalBody: true,
  'a-button': { template: '<button><slot /></button>' },
  'a-card': { template: '<div class="quick-link-card"><slot /></div>' },
  'a-modal': { template: '<div class="a-modal-stub"><slot /></div>', props: ['open'] },
}

const factory = () => mount(ApplicantDutyIntroduction, { global: { stubs } })

describe('ApplicantDutyIntroduction', () => {
  it('7개 직무 카드를 렌더한다', () => {
    const w = factory()
    expect(w.findAll('.quick-link-card')).toHaveLength(7)
  })

  it('카드에 국문 타이틀과 영문 eyebrow를 바인딩한다', () => {
    const w = factory()
    const titles = w.findAll('.card-title').map((n) => n.text())
    const descs = w.findAll('.card-desc').map((n) => n.text())
    expect(titles).toContain('자산관리 서비스')
    expect(titles).toContain('IB')
    expect(descs).toContain('Wealth Management Service')
    expect(descs).toContain('Investment Banking')
  })
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `npm run test:unit -- run src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts`
Expected: FAIL — `.card-desc`가 영문 eyebrow가 아닌 기존 값이거나, 카드 타이틀이 `formatCardTitle` 분해 결과라 `'IB'`/`'자산관리 서비스'` 단정 불일치

- [ ] **Step 3: `<script setup>` 블록 교체**

`src/views/applicant/ApplicantDutyIntroduction.vue`의 `<script setup lang="ts">` 전체(기존 63~242행)를 아래로 교체한다. (기존 `QuickLinkItem` 인터페이스, `HtmlView` import, `formatCardTitle` 함수 제거 / `DutyItem` import 및 `DutyIntroModalBody` import 추가)

```ts
import { ref } from 'vue'
import ApplicantBreadcrumb from '@/views/applicant/ApplicantBreadcrumb.vue'
import { useRouter } from 'vue-router'
import DutyIntroModalBody from '@/views/applicant/DutyIntroModalBody.vue'
import type { DutyItem } from '@/types/duty'

const router = useRouter()

const goRecruits = async (): Promise<void> => {
  await router.push('/applicant/recruits')
}

const modalStatus = ref<Record<string, boolean>>({})

const modalOpen = (url: string): void => {
  modalStatus.value[url] = true
}

const modalClose = (url: string): void => {
  modalStatus.value[url] = false
}

const dutyList = ref<DutyItem[]>([
  {
    url: 'Wealth Management',
    eyebrow: 'Wealth Management Service',
    title: '자산관리 서비스',
    major: '전공 무관',
    degree: '학사 이상',
    intro: 'WM(Wealth Management) 직무는 고객 중심의 자산관리 서비스 제공을 핵심 추진과제로 하며 포트폴리오를 기반으로 한 상품 및 투자 상담, 세무/부동산 컨설팅, 자산승계 등 종합적인 자산관리 서비스를 제공하는 업무를 수행합니다. 고객을 직접 대면하여 소통하는 영업뿐 아니라 다양한 투자안을 분석하고 포트폴리오를 수립하거나, 효과적으로 고객 자산관리를 할 수 있는 다양한 방안을 모색, 기획하는 일을 함께 합니다.',
    skills: [
      { lead: '공감 능력 및 커뮤니케이션 역량:', body: '항상 고객 중심으로 사고하는 직무이므로 무엇보다도 고객 지향 마인드, 고객에 대한 공감 능력 및 유연한 커뮤니케이션 역량이 중요합니다.' },
      { lead: '문제해결 능력:', body: '자산관리 분야는 항상 새로운 과제에 대해 유연하게 사고하고 솔루션을 제공해야 하기 때문에 문제 사항을 잘 이해하고 고민하여 가장 적합한 솔루션을 도출하는 능력과 문제에 대한 도전정신이 필요합니다.' },
      { lead: '윤리의식:', body: '눈앞의 성과보다는 고객에게 최적의 솔루션을 제안하기 위해 높은 수준의 도덕성과 윤리의식이 필요합니다.' },
    ],
    career: '자산관리 분야는 영업뿐만 아니라 다양한 업무를 직간접적으로 경험할 수 있습니다. 고객에게 최적의 솔루션을 제공하기 위해 금융상품 투자뿐만 아니라 세무, 부동산 컨설팅, IB 등 다양한 분야의 지식을 쌓을 수 있고, 이를 통해 자산관리 서비스의 전문가로 성장할 수 있습니다. 다양한 현장 경험은 영업 기획, 전략, 마케팅, 교육 등 여러 분야로의 커리어 확장 기회를 제공합니다.',
    diff: '신영증권 WM 부문은 고객 이해를 기반으로 장기적 관점에서 고객이 필요로 하는 솔루션 제공에 집중합니다. 주식 영업, 금융상품 판매가 아닌, 팀 단위 자산관리와 본사 전문가 협업을 통해 최적의 솔루션을 찾고 이를 통해 고객만족을 높이는 것을 우선 가치로 생각합니다. 신영증권 WM 부문에서는 자산관리에서 승계까지 아우르는 다양한 과정을 경험하고 전문성을 키울 수 있는 기회를 제공하고 있어, 남다른 경쟁력을 가진 인재로 성장할 수 있습니다.',
  },
  {
    url: 'Institutional Sales',
    eyebrow: 'Institutional Sales',
    title: '본사영업',
    major: '무관',
    degree: '학사 이상',
    intro: '본사영업은 기관투자자를 대상으로 하는 영업으로 주식, 채권, 파생상품 등을 중개, 판매하는 영업과 구조화 상품을 발행하여 판매하는 영업이 있습니다. 중개 영업의 주요 고객은 연기금, 운용사, 보험사이며 리서치센터와 협업하여 고객에게 필요한 시장정보와 투자 아이디어도 제공하고 있습니다. 팀 중심 영업활동을 통해 시장 점유율 확대와 수익성 다양화를 목표로 하고 있습니다. 구조화 상품을 판매하는 영업은 국내 및 해외시장에서 거래되는 다양한 기초자산을 다루며, 국내 및 외국계 IB와 주로 거래합니다.',
    skills: [
      { lead: '공감 및 커뮤니케이션 역량:', body: '부서원과 긴밀한 협업이 필요하며, 고객의 니즈를 파악하고 여러 주체의 요구사항을 조율해야 하기 때문에 커뮤니케이션 능력이 중요합니다.' },
      { lead: '금융시장에 대한 이해:', body: '기관투자자 및 국내외 IB를 대상으로 영업하기 때문에 높은 수준의 경제/재무/금융상품/파생상품에 대한 이해가 필요합니다.' },
      { lead: '외국어 역량:', body: '외국계 IB를 대상으로 하는 영업에 있어 외국어 역량은 필수입니다.' },
    ],
    career: '영업의 가장 큰 장점은 대내외로 다양한 네트워크를 쌓을 수 있고, 리서치 및 상품을 공급하는 부서는 물론 대내외 다양한 금융전문가와의 소통으로 지식을 쌓고 견문을 넓힐 수 있다는 것입니다. 전통적인 금융자산뿐만 아니라 구조화된 상품을 접하면서 습득한 지식, 세일즈를 하면서 형성한 네트워크는 향후 금융 전문가로 성장하는데 중요한 자산이 됩니다. 또한 영업을 통해 쌓은 네트워크를 기반으로 기금, 보험사, 공사, 은행 및 제조업의 자금 운용팀, 재무팀, IR팀으로 커리어 전환도 가능합니다.',
    diff: '신영증권은 오랜 기간 기관 세일즈에 강점을 가지고 있는 종합증권사로 금융시장에서 거래하는 모든 상품을 발행, 중개, 판매하고 있습니다. 세일즈 부서 간 협업을 통한 시너지 창출을 오랜 전통으로 여기고 있고 폭넓은 상품을 취급하고 있어, 여러 영역의 세일즈를 경험할 수 있습니다. 오랜시간 고객과 이어진 관계 위에서 새로운 네트워크를 쌓아갈 수 있습니다.',
  },
  {
    url: 'Investment Banking',
    eyebrow: 'Investment Banking',
    title: 'IB',
    major: '경영/회계/경제',
    degree: '학사 이상',
    intro: 'IB는 기업의 자금 수요자와 자금 공급자를 중개하는 업무를 핵심 비즈니스로 하며 자금 조달의 형태에 따라 채권, 대출, 주식, 구조화증권 등과 관련된 부서로 구성되어 있습니다. 자금을 조달하는 전 과정에 참여하여 수요자에게 가장효과적인 조달 방법을 설계하고 자문하는 서비스를 제공하며, 발행된 유가증권 등을 공급자에게 세일즈하는 역할까지 수행합니다. 이 과정에서 양자간의 니즈를 파악하고 조율하며, 거래 구조를 설계하고, 기업과 산업을 숫자로 표현하고 분석하며, 감독원·거래소 등 유관 기관에 대응하는 업무 등을 수행하게 됩니다.',
    skills: [
      { lead: '회계지식과 분석적 사고력:', body: '경제 및 자본시장에 대한 관심과 재무제표를 정확히 해석할 수 있는 회계지식이 필요합니다. 회계/재무 지식에 더하여 분석적 사고를 통해 기업가치를 평가할 수 있어야 합니다.' },
      { lead: '문서 작성 능력:', body: '분석한 내용을 정확하고 신속하게 보고서로 작성할 수 있어야 합니다.' },
      { lead: '커뮤니케이션 역량:', body: '자금 수요자, 투자자, 감독기관 등 다양한 주체의 요구사항을 적절하게 조율하고 쉽게 설명할수 있어야 합니다.' },
      { lead: '문제해결 능력:', body: '기업마다 경영환경과 사업내용이 다르므로 매번 다양한 문제사항에 직면하게 됩니다. 따라서 적극적으로 문제를 해결하려는 자세가 필요합니다.' },
    ],
    career: '재무제표 및 매크로를 분석하고 여러 구조화된 금융상품을 접하면서 기업, 산업, 금융시장에 대한 분석력을 키워 시야를 넓혀갈 수 있습니다. 기업의 CEO, CFO 등 주요 의사결정자와 커뮤니케이션하며 형성한 네트워크는 금융 전문가로 성장할 수 있는 중요한 자산입니다. 향후 Investment Banker로 지속 성장(Sell-side)하거나 운용부서(Buy-side)로 업무를 확장할 수 있습니다. 딜 구조, 투자 상품 등에 내재된 위험과 효익을 분석하고 승인을 내는 심사역, 기업·산업에 대한 분석 보고서를 내는 애널리스트 등으로 직무 전환도 가능합니다.',
    diff: '신영증권은 종합 증권사로 다양한 IB업무(장단기채권, CP, IPO, 구조화증권, 부동산, 인수금융, 기업금융, VC, PE 등)를 영위하고 있습니다. IB업무의 여러 영역을 직·간접적으로 경험해 볼 수 있어, 역량 및 적성에 적합한 분야를 찾아볼 수 있는 기회가 있습니다. 고객과의 신뢰를 지키기 위해 심도 있는 토론과 분석으로 상품을 선별하고 있어 폭넓고 깊이 있는 IB 관련 지식을 함양할 수 있습니다.',
  },
  {
    url: 'Finance for Strategy',
    eyebrow: 'Finance for Strategy',
    title: '운용',
    major: '무관',
    degree: '학사 이상(석사, 박사 우대)',
    intro: '운용은 재원에 따라 고유자산을 운용하는 경우와 외부에서 자금을 조달하여 운용하는 경우로 나눌 수 있습니다. 운용대상이 되는 자산은 주식, 채권, 외환, Commodity 및 관련 파생상품까지 다양합니다. 운용은 금융에 대한 전문적인 지식을 바탕으로 다양한 정보를 수집하여 트레이딩을 하거나 투자 전략을 개발하여 신상품을 기획하고, 신규 투자 대상을 선별하여 발굴하는 일을 합니다.',
    skills: [
      { lead: '금융시장에 대한 관심과 수리적 감각:', body: '국내 및 글로벌 금융시장에 대한 이해와 관심이 높아야 하며, 수리적 감각 및 분석 능력을 갖추고 있어야 합니다.' },
      { lead: '문제해결 능력:', body: '제한된 정보와 시간 속에서 최적의 운용 솔루션을 도출해내기 위한 창의성이 필요하며, 시장에서 벌어지는 예측 불가능한 상황에 냉정하게 판단하고 대처하는 능력이 필요합니다.' },
      { lead: '학습 능력:', body: '다루는 상품이 다양하고 광범위하다 보니 호기심을 가지고 지속적으로 새로운 지식을 익히고 습득해야합니다.' },
    ],
    career: '다양한 밸류에이션 기법과 분석 능력으로 운용 및 관리 노하우를 습득할 수 있습니다. 파생상품 운용을 통해서는 금융 공학, 구조화 상품에 대한 이해를 훈련하게 됩니다. 주어진 한도 내에서 상품을 거래하며 손익 및 리스크 관리 역량을 키우고 종합적인 판단 능력을 익히게 됩니다. 향후 본인의 역량과 적성에 따라 트레이더, 퀀트 전문가, 자산운용역으로 커리어를 선택하여 성장할 수 있으며 PE, VC, 리스크관리 등 다양한 관련 직무로도 확장이 가능합니다.',
    diff: '신영증권은 펀더멘털 분석을 바탕으로 리스크 대비 안정적인 수익을 지향합니다. 모멘텀 투자보다는 시장 리서치, 기업 분석 등을 통해 회사 투자철학에 적합한 투자 및 전략을 활용합니다. 수리적 감각과 분석적 사고능력을 갖추고 있다면 전문지식이 부족하여도 내부 교육 훈련 프로그램을 통해 전문가로 성장할 수 있고, 우리 회사만의 축적된 운용 노하우와 파생상품 모델링 능력을 바탕으로 여러 금융상품에 대한 심도있는 지식을 함양할 수 있습니다.',
  },
  {
    url: 'Research Center',
    eyebrow: 'Research Center',
    title: '리서치센터',
    major: '무관',
    degree: '학사 이상',
    intro: '리서치센터는 개별 자산과 시장, 기업 및 산업분석을 수행하는 조직으로 산업분석팀과 자산전략팀으로 구성되어 있습니다. 산업분석팀은 주요 산업과 관련 기업에 대한 조사분석 업무를 하며, 최근 분석의 범위가 해외 기업까지 확장되고 있습니다. 산업분석팀은 미시적 접근(Bottom-up)을 통한 가치 평가를 지향합니다. 자산전략팀은 주식과 채권, 원자재 등 개별 자산에 대한 투자 의견 제시 및 자산배분 업무를 수행하고 있으며, 거시적 방법론(Top-down)으로 투자 의견을 제시하고 있습니다. 리서치센터 애널리스트는 기업 탐방과 세미나 등 외부 미팅이 많고, 리포트와 동영상 등 다양한 매체로 의견을 개진합니다. 사내 유관 부서, 기관투자자, 개인투자고객, 미디어 등 다양한 이해관계자들이 리서치센터의 고객입니다.',
    skills: [
      { lead: '경제학적 소양 및 재무분석 능력:', body: '다양한 경제 현상을 해석할 수 있는 거시경제 관련 지식과 기업분석 및 실적 추정을 위한 재무, 회계 지식을 기본적으로 갖추어야 합니다.' },
      { lead: '외국어 역량:', body: '해외 시장, 산업, 기업분석 자료를 읽고 해석할 수 있는 수준의 외국어 역량이 필요합니다.' },
      { lead: 'OA 역량:', body: '다양하고 방대한 양의 데이터를 관리하고 분석하기 위해 OA 작업에 능숙해야 합니다.' },
      { lead: '성실, 근면한 태도 및 준법정신:', body: '주어진 시간 내에 업무를 완료하고자 하는 책임감과 강한 체력, 미공개 정보를 주로 다루기 때문에 정보 관련 윤리, 준법정신을 갖추고 있어야 합니다.' },
    ],
    career: '애널리스트로 활동하며 시장과 산업에 대한 통찰을 쌓을 수 있습니다. 또한 다양한 외부고객 및 사내 부서와 협업하므로 본인의 적성과 성과에 따라 중장기적으로는 애널리스트뿐만 아니라 자산운용, IB, 기업체 IR, 기업체 전략/기획 등으로 경력을 확장할 수 있습니다.',
    diff: '신영증권 리서치센터는 오랜 역사와 전통을 가지고 있으며, RA(Research Assistant)로 시작하여 JA(Junior Analyst), SA(Senior Analyst)가 되는 전 과정에서 체계적인 OJT 프로그램을 통해 동료 및 선배 애널리스트들의 경험과 지식 및 노하우를 전수받을 수 있는 기회를 제공하고 있습니다. 또한 틀에 박히지 않은 보고서를 작성할 수 있도록 교육 및 각종 인프라를 제공함으로써 자본시장의 발전에 발맞추어 본인의 역량을 무한 강화할 수 있습니다.',
  },
  {
    url: 'IT Center',
    eyebrow: 'IT Center',
    title: 'IT센터',
    major: '컴퓨터 계열 전공 또는 관련 과목 이수자',
    degree: '학사 이상',
    intro: 'IT는 고객에게 최고의 금융 경험을 제공하고, 직원들에게는 유연한 업무환경을 조성하기 위한 시스템을 개발하고 관리, 운영합니다. 보안시스템 구축을 통해 금융 데이터를 안전하게 지키고, 최신 인프라를 구축하여 안정적인 시스템 운영을 책임집니다. 또한, 고객용 다양한 채널(MTS, HTS, 홈페이지 등) 개발과 더불어 매매/운용/증권업무 관련 시스템 개발, 설계를 통해 금융의 미래를 선도하는 핵심적인 업무를 수행합니다. 기존 시스템의 유지보수뿐 아니라, 최신 기술 트렌드를 반영한 프로젝트 참여를 통해 끊임없이 성장하고 발전할 수 있는 기회를 제공합니다.',
    skills: [
      { lead: '인프라 지식 및 프로그래밍 역량:', body: 'IT 인프라(서버, 네트워크, DBMS 등)에 대한 기초 지식과 프로그래밍 능력이 필요합니다.' },
      { lead: '신기술에 대한 관심 및 자기개발 의지:', body: 'Cloud Computing, Big Data, Block Chain, AI 등 지속적으로 발전하는 신기술에 대해 관심을 갖고 자기개발을 하고자 하는 의지가 있어야 합니다.' },
      { lead: '금융에 대한 관심:', body: '금융 분야에 관심을 가지고 있어야 하며, 금융업을 이해하고자 하는 의지가 필요합니다.' },
      { lead: '커뮤니케이션 역량:', body: '유관기관 및 부서와 많은 협업을 수행하게 되므로 커뮤니케이션 역량, 절차적 사고, 일정 관리 능력 등이 필요합니다.' },
    ],
    career: 'IT 센터는 담당 직무에 따라 크게 두 가지 방향으로 커리어 설계가 가능합니다. 보안, 인프라 관리, 시스템 프로그래밍 등의 직무는 IT에 특화된 전문가로 성장이 가능합니다. 각종 고객 서비스에 대한 시스템 설계부터 개발, 운영과 관련된 직무를 통해서는 증권업에 특화된 IT 전문가로 성장이 가능합니다.',
    diff: '신영증권 IT센터는 업계 최초로 주요 시스템을 클라우드 전환하고 종합신탁 체계를 구축하였으며, 디지털 트랜스포메이션을 위한 다양한 노력으로 업계 선도적인 역할을 수행해오고 있습니다. 새로운 기술에 대한 적극적인 도입과 필요한 기술에 대한 선택과 집중으로 선도적인 역할 수행이 가능한 전문가로 성장할 수 있도록 지원합니다.',
  },
  {
    url: 'Management & Operation',
    eyebrow: 'Management & Operation',
    title: '본사관리',
    major: '무관',
    degree: '학사 이상',
    intro: '증권업 본연의 업무를 수행하기 위해서는 다양한 지원/통제 부서가 필요합니다. 고객에게 제공하는 상품 및 서비스를 발굴하고 판매 전략을 수립하는 부서, 증권 움직임에서 파생되는 매매, 권리, 자금업무 등을 담당하는 부서, 회사의 신규 투자에서 발생 가능한 위험 요소(Risk factor)를 사전에 분석하고, 이를 바탕으로 해당 투자에 대한 합리적인 의사 결정을 지원하는 부서 등이 있습니다. 일반적으로 기업 운영에 요구되는 역할을 하는 부서들도 있습니다. 회사 전반의 경영현황을 점검하고 핵심 의제를 해결하며 전략을 기획하는 부서, 인적자원을 배분 관리하는 부서, 재무자원을 효율적으로 조달·운용하는 부서, 주주총회와 이사회를 관리하고, 경영활동을 지원하는 부서가 이에 해당합니다.',
    skills: [
      { lead: '커뮤니케이션 역량:', body: '유관부서, 대외기관, 감독 당국 등 다양한 주체와 효과적으로 정보와 의견을 교환할 수 있는 역량이 필요합니다.' },
      { lead: '호기심과 통찰력:', body: '증권업과 금융시장에 대한 호기심과 관심이 필요합니다. 이를 바탕으로 데이터를 수집, 가공하고 창의적으로 인사이트를 도출함으로써 효과적인 의사결정을 지원해야 합니다.' },
      { lead: '분석적 사고력 및 적극적 태도:', body: '많은 양의 문서와 데이터를 검토해야 하며, 문제의식과 책임감을 가지고 접근하는 적극적인 마인드가 요구됩니다.' },
      { lead: 'OA 및 외국어 역량:', body: '데이터를 분석하고 이를 정리하기 위해 OA 작업에 능숙해야 하며, 외국 자료를 검토하거나 거래 상대방과의 소통을 위해 기본적인 외국어 역량을 갖추고 있어야 합니다.' },
    ],
    career: '본사관리 부서에서 커리어를 시작하는 경우, 해당 업무에 전문가로 성장할 수도 있지만 외연을 확장하여 더욱 다양한 커리어패스를 설계할 수 있습니다. 대내외 다양한 주체와 커뮤니케이션하여 네트워크를 쌓을 수 있고, 여러 금융상품을 검토하거나 사업 분야를 접할 수 있는 기회가 있습니다. 이를 통해 본인이 가지고 있는 역량을 발굴하고 다양한 방면으로 커리어를 개발할 수 있습니다.',
    diff: '신영증권 본사관리의 차별화된 점은 관심과 호기심, 도전정신만 갖추고 있다면 다양한 업무를 경험해 볼 수 있다는 점입니다. 특정 직무에 국한되어 일하지 않고 부서 간 업무 경계 없이 여러 구성원들이 참여하여 문제를 해결하고 업무를 진행해 나갈 수 있습니다. 이를 통해 다양한 시각과 역량을 가진 사람들과 일해볼 수 있는 기회를 가질 수 있으며, 입체적으로 문제에 접근하고 해결하는 훈련이 가능합니다. 여러 분야의 전문가들과 커뮤니케이션하면서 다양한 일을 직접 경험해 보고 싶은 사람들에게 적합한 포지션입니다.',
  },
])
```

> 비고: 본사관리 `diff`의 마지막 문장은 기존 데이터에 어순이 깨진 채("...훈련이 가능합니다 여러 분야의 전문가들과 . 커뮤니케이션하면서...") 있었으나, 위에서는 자연스러운 어순으로 정리했다. 의미·단어는 동일하며 추가/삭제 없음.

- [ ] **Step 4: `<template>`의 카드 바인딩과 모달 블록 교체**

(A) 카드 타이틀/설명 바인딩 — 기존(28~29행):

```html
<h3 class="card-title">{{ formatCardTitle(item.title)[0] }}</h3>
<p class="card-desc">{{ formatCardTitle(item.title)[1]?.replace(')','') }}</p>
```

를 아래로 교체:

```html
<h3 class="card-title">{{ item.title }}</h3>
<p class="card-desc">{{ item.eyebrow }}</p>
```

(B) 모달 블록 — 기존(48~60행) 전체:

```html
  <a-modal
      :getContainer="false"
      v-for="item of dutyList"
      :key="item.url"
      v-model:open="modalStatus[item.url]"
      :title="item.title"
      :width="750"
      @ok="modalOpen(item.url)"
      @cancel="modalClose(item.url)"
      >
      <!-- <p v-html="item.description"></p> -->
      <HtmlView :content="item.description"/>
   </a-modal>
```

를 아래로 교체:

```html
  <a-modal
      :getContainer="false"
      v-for="item of dutyList"
      :key="item.url"
      v-model:open="modalStatus[item.url]"
      :width="750"
      :closable="false"
      :footer="null"
      @cancel="modalClose(item.url)"
      >
      <DutyIntroModalBody :job="item" @close="modalClose(item.url)" />
   </a-modal>
```

- [ ] **Step 5: 모달 패딩 스타일 조정**

`<style scoped>`에서 기존 `:deep(.ant-modal-title)`, `:deep(.ant-modal-content)`, `:deep(.ant-modal-body)` 블록(기존 439~475행 영역)을 아래로 교체한다. (모달 내부 패딩/스크롤은 이제 `DutyIntroModalBody`가 자체적으로 가지므로 Ant 컨테이너 패딩을 0으로 둔다.)

```css
:deep(.ant-modal-content) {
  padding: 0;
  overflow: hidden;
  border-radius: 12px;
}

:deep(.ant-modal-body) {
  padding: 0;
}

:deep(.ant-modal-footer) {
  display: none;
}
```

- [ ] **Step 6: 부모 테스트 통과 확인**

Run: `npm run test:unit -- run src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts`
Expected: PASS (2 tests)

- [ ] **Step 7: 타입 체크**

Run: `npm run type-check`
Expected: PASS — `dutyList`의 7개 항목이 모두 `DutyItem` 형태를 만족, 제거된 `HtmlView`/`formatCardTitle`/`QuickLinkItem` 참조 잔존 없음

- [ ] **Step 8: 커밋**

```bash
git add src/views/applicant/ApplicantDutyIntroduction.vue src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts
git commit -m "feat(duty): 직무소개 모달 신규 디자인 적용 및 데이터 구조화"
```

---

## Task 4: 전체 검증

**Files:** (없음 — 검증 전용)

- [ ] **Step 1: 관련 테스트 전체 실행**

Run: `npm run test:unit -- run src/views/applicant/__tests__/DutyIntroModalBody.spec.ts src/views/applicant/__tests__/ApplicantDutyIntroduction.spec.ts`
Expected: PASS (7 tests 합계)

- [ ] **Step 2: 빌드(타입체크 포함)**

Run: `npm run build`
Expected: 성공 (vue-tsc 타입체크 + vite build 통과)

- [ ] **Step 3: 수동 확인 체크리스트 (dev 서버, 선택)**

Run: `npm run dev` 후 지원자 직무소개 화면 진입.
- [ ] 카드 7개 외형이 기존과 동일(국문 타이틀 + 영문 설명).
- [ ] `상세보기` 클릭 시 새 디자인 모달이 뜬다(eyebrow + 국문 타이틀 + ①②③ 섹션 + 전공/학위 pill + 마름모 스킬 + 차별점 박스).
- [ ] 각 직무별 eyebrow/타이틀/본문이 올바르게 매핑.
- [ ] 커스텀 ✕ 클릭 시 모달이 닫힌다.
- [ ] 본문이 길 때 모달 내부가 스크롤된다.

---

## 검증 정책 참고

- 백엔드/계약 변경 없음 → `api-contract.md` 갱신 불필요(설계서 §7 "API 무관").
- AGENTS.md 준수: `<script setup lang="ts">`, scoped 스타일, ant-design-vue 유지, 한글 텍스트 보존, 작은 컴포넌트 분리.

## Definition of Done

- 7개 직무 모달이 새 디자인으로 렌더되고 본문 카피가 보존된다.
- 카드 그리드·메인비주얼 외형 불변, 모달 열기/닫기 정상.
- `npm run test:unit`(관련 스펙) PASS, `npm run build` 성공.
- 제거된 `HtmlView`/`formatCardTitle`/`QuickLinkItem` 잔존 참조 없음.
