# 직무소개 모달 리디자인 설계서

- 날짜: 2026-06-29
- 대상: 프론트엔드 `recruit_front` — 지원자 직무소개 화면의 상세 모달
- 기준 디자인: `recruit/dist/신영증권 직무소개 모달.html` (self-extracting 번들, 추출본 `recruit/dist/_extracted/duty__entry.html` / `duty__xdc00.html`)
- 유형: 프론트 단독 슬라이스 (API/계약 영향 없음)

## 1. 목적

지원자 `직무소개` 화면에서 `상세보기` 클릭 시 뜨는 **각 직무 상세 모달**의 디자인을 기준 HTML(`신영증권 직무소개 모달.html`)에 맞춰 전면 교체한다. 7개 직무 전부 동일한 새 디자인으로 렌더하되, 기존 본문 카피는 100% 보존한다.

## 2. 범위 (In scope)

- `ApplicantDutyIntroduction.vue`의 모달 콘텐츠를 기준 디자인으로 교체.
- 모달 데이터 모델을 통 HTML 문자열(`description`) → 구조화 필드로 재구조화 (7개 직무 전부).
- 신규 자식 컴포넌트 `DutyIntroModalBody.vue` 추가 (모달 본문 렌더 전담).
- a-modal 셸 조정(기본 헤더/닫기 숨김, 커스텀 헤더 사용).
- 카드 바인딩을 새 데이터 필드로 교체(외형 유지를 위한 최소 수정).

## 3. 비범위 (Out of scope)

- 카드 그리드·메인비주얼(`main-visual`)·페이지 타이틀의 **디자인 변경** — 외형 그대로 유지.
- 라우팅, 스토어, API, 백엔드, `api-contract.md` — 변경 없음.
- 직무 본문 **카피(텍스트) 자체의 수정/윤문** — 기존 문구 보존(형태만 분해, 명백한 정규화 제외).
- 다른 화면/컴포넌트.

## 4. 기준 디자인 구조 (추출 결과)

직무 1개당 모달:

- **헤더**: 녹색 점(`#2f6f55`) + 영문 eyebrow 라벨(대문자 letter-spacing, 예: "Wealth Management Service") → 국문 타이틀(예: "자산관리 서비스", 26px/800) + 우상단 커스텀 ✕ 버튼.
- **본문**(스크롤, max-height ~560px):
  - ① **직무소개**: 녹색 번호 뱃지(`#0f4726` 사각, 흰 숫자 "1") + 제목 + 단락.
  - ② **필요역량**: 번호 뱃지 "2" + 제목, 그 아래
    - pill 칩 2개: `전공 {major}`, `학위 {degree}` (연녹 배경 `#f4f8f0`, 라운드 999px).
    - 스킬 리스트: 항목별 45° 회전 녹색 마름모 불릿 + `<b>{lead}</b> {body}`.
  - ③ **커리어패스**: 번호 뱃지 "3" + 제목 + 단락.
  - **차별점("신영증권만의 차별점")**: 번호 뱃지 없음. 연녹 하이라이트 박스(`#f8faf6`, border `#e7efe2`, 라운드 12px) 안에 녹색 제목 + 단락.

색 토큰: 진녹 `#0f4726`(뱃지·강조 제목), 녹색 `#2f6f55`(eyebrow·불릿 — 기존 파일에서 이미 사용), 연녹 배경 `#f4f8f0`/`#f8faf6`, 경계 `#dfe5dc`/`#e7efe2`, 본문 텍스트 `#374151`, 제목 `#1f2937`.

기준 파일의 `style-hover`, `sc-for`, `{{ }}` 머스태시는 목업 도구 의사문법 → 실제 Vue(`v-for`, 바인딩)·CSS(`:hover`)로 치환.

## 5. 데이터 모델

```ts
interface DutySkill {
  lead: string   // 굵게 표시될 선두 (예: "공감 능력 및 커뮤니케이션 역량:")
  body: string   // 이어지는 설명
}

interface DutyItem {
  url: string          // 기존 키 유지 — 모달 open/close 상태 매핑에 사용
  eyebrow: string      // 헤더 영문 라벨 — 기존 타이틀 괄호 안 값
  title: string        // 국문 타이틀 — 기존 타이틀 괄호 앞 값
  major: string        // "전공:" 값 (pill)
  degree: string       // "학위/학력:" 값 (pill)
  intro: string        // ① 직무소개 본문
  skills: DutySkill[]  // ② 필요역량 항목들
  career: string       // ③ 커리어패스 본문
  diff: string         // 차별점 본문
}
```

`dutyList`는 컴포넌트 인라인 정적 데이터로 유지(백엔드 비연동). 기존 7개 항목을 위 형태로 변환.

### 5.1 기존 → 신규 매핑 규칙

| 기존 | 신규 |
|---|---|
| `title` "국문 (English)" | `title`=괄호 앞 trim, `eyebrow`=괄호 안 trim |
| `description`의 `<h3>1.직무소개</h3><p>…</p>` | `intro` |
| `<p>전공: …</p>` | `major` (라벨 "전공:" 제거한 값) |
| `<p>학위/학력: …</p>` | `degree` (라벨 제거한 값) |
| `<ul><li><b>lead:</b> body</li>…` | `skills[]` = `{lead, body}` |
| `<h3>3.커리어패스</h3><p>…</p>` | `career` |
| `<h3>4.신영증권만의 차별점</h3><p>…</p>` | `diff` |
| `url` | `url` 그대로 |

### 5.2 정규화(명백한 오류만)

기존 데이터 보존이 원칙이나, 형태 분해 과정에서 드러나는 명백한 결함만 정규화:

- `학력:` / `학위:` 혼용 → 둘 다 `degree` 필드로 흡수(라벨은 템플릿에서 "학위"로 통일 표기).
- 미닫힌 `<li>`(본사영업·IB·운용·리서치·IT·본사관리 일부) → `skills` 배열 항목으로 정상 분해.
- 본사관리 `diff` 말미의 오탈자성 문장 순서/잘린 `<p>`는 **원문 텍스트 그대로** 옮기되 마크업만 정리(문구 수정 아님).

## 6. 컴포넌트 설계

### 6.1 `ApplicantDutyIntroduction.vue` (부모, 수정)

- 카드 그리드·`main-visual`·breadcrumb·페이지 타이틀: **그대로 유지**.
- `dutyList` 타입을 `DutyItem[]`로 교체, 7개 항목 재구조화.
- 카드 바인딩 수정(외형 유지 목적의 최소 변경):
  - 기존: `formatCardTitle(item.title)[0]` / `[1]?.replace(')','')` (괄호 split — 깨지기 쉬움).
  - 신규: 카드 제목 = `item.title`(국문), 카드 설명 = `item.eyebrow`(영문). `formatCardTitle` 헬퍼 제거.
- a-modal:
  - `:title` 제거, `:closable="false"`(기본 헤더·닫기 숨김), `:width="750"` 유지, footer 기존대로 숨김.
  - 본문에 `<DutyIntroModalBody :job="item" @close="modalClose(item.url)" />` 렌더. (기존 `HtmlView` 제거.)
- `modalStatus`/`modalOpen`/`modalClose`/`goRecruits` 로직 유지.

### 6.2 `DutyIntroModalBody.vue` (신규 자식)

- `<script setup lang="ts">`, `defineProps<{ job: DutyItem }>()`, `defineEmits<{ (e:'close'): void }>()`.
- 템플릿: 기준 디자인의 카드 내부(헤더 + 본문 3섹션 + 차별점 박스). ✕ 버튼 `@click="emit('close')"`.
- 스킬은 `v-for="s in job.skills"`.
- 스타일: 기준 인라인 → scoped CSS 클래스로 변환. 색은 로컬 CSS(또는 상수). Ant 내부 override 필요 시 부모에서 `:deep()`.
- `DutyItem`/`DutySkill` 타입은 기존 컨벤션(`src/types/*.ts` 도메인 모듈, 예: `jobPosting.ts`)에 맞춰 신규 `src/types/duty.ts`에 선언하고 부모·자식이 `@/types/duty`에서 import.

### 6.3 a-modal 내부 레이아웃 주의

기준 디자인의 최외곽 래퍼(전체화면 flex 센터링 + 배경)는 a-modal이 제공하는 백드롭/센터링과 중복 → **제거**하고, 기준의 흰 카드(`max-width:760`) 내부만 모달 body로 매핑. a-modal `padding`은 `:deep(.ant-modal-content)`에서 0 처리하여 기준의 자체 패딩(헤더 32/40, 본문 28/40)을 살린다. 기존 스크롤바 스타일(`.ant-modal-body` webkit-scrollbar)은 본문 `.dm-scroll`로 이관하거나 유지.

## 7. 성공 기준

- 7개 직무 모달 전부 새 디자인으로 렌더, 본문 카피 100% 보존.
- 카드 그리드·메인비주얼 외형 불변. `상세보기` → 모달 열기, 커스텀 ✕ → 닫기 정상.
- 모달 헤더 eyebrow/타이틀이 직무별로 정확히 매핑.
- `npm run type-check` 통과 (필요 시 `npm run build`).
- API/계약 영향 없음(백엔드·`api-contract.md` 변경 0).
- 기존 라우트·스토어·한글 텍스트 보존, ant-design-vue 유지(AGENTS.md 준수).

## 8. 검증

```bash
# recruit_front/
npm run type-check
npm run build   # 필요 시
```

수동 확인(선택): dev 서버에서 직무소개 → 각 직무 상세보기 → 모달 디자인/스크롤/닫기 육안 확인.

## 9. 남은 이슈 / 결정 사항

- eyebrow 영문은 기존 타이틀 괄호 값에서 도출(전 직무 괄호 존재 확인됨). 누락 직무 발견 시 `url` 기반 보강.
- 차별점 박스는 번호 미부여(기준 디자인 의도) — ①②③만 번호.
- 반응형: 기준 max-width 760 / a-modal width 750. 좁은 화면은 a-modal 기본 반응형 + 본문 스크롤로 처리.

## 10. 다음 단계

본 설계 승인 후 writing-plans 스킬로 구현 계획서 작성 → 구현.
