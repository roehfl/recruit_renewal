# 복리후생·교육·평가 화면 v2 전면 교체 — 설계서

- 작성일: 2026-06-24
- 대상 레포: `recruit_front/` (프론트 단독 슬라이스)
- 기준 목업: `recruit/dist/복리후생·교육·평가 화면.html` (추출본: `recruit/dist/_extracted/entry.html`)

## 1. 목표 / 배경

신영증권 채용 사이트의 3개 안내 화면(복리후생·교육·평가)을 기존 단순 탭+불릿 리스트에서,
목업(`복리후생·교육·평가 화면.html`) 기준의 새 디자인(탭 + 카테고리 칩 + 카드형 리스트)으로 **전면 교체**한다.

- 기존 v1/v2 토글 아이디어는 **취소**. 기존 탭 데이터/로직을 버리고 새 디자인으로 대체한다.
- 3개 화면이 새 디자인에서 구조(탭·칩·카드·스타일)가 거의 동일하므로, **공통 프레젠테이션 컴포넌트 1개를 추출**하고 페이지 3개는 데이터만 공급하는 얇은 래퍼로 만든다.

## 2. 범위 / 비범위

### 범위
- 공통 컴포넌트 `ApplicantInfoTabPanel.vue` 신설.
- 공유 타입 모듈 `infoTabPanel.ts` 신설.
- `ApplicantBenefits.vue`, `ApplicantTraining.vue`, `ApplicantHrRule.vue` 3개 재작성(데이터 래퍼화).
- `src/styles/variables.scss`에 액센트 색 토큰 5종 **추가**(기존 값 수정 없음).

### 비범위 (영향 없음)
- 라우터: 파일명·경로 유지 → 라우트 수정 없음.
- API / `api-contract.md`: 정적 콘텐츠 화면이라 백엔드 호출 없음 → **계약 영향 없음**.
- 백엔드: 변경 없음.
- AntD: 이 3개 화면은 기존에도 커스텀 HTML/CSS(불릿 리스트, 커스텀 탭 버튼)였고 목업도 동일 → AntD 컴포넌트 도입하지 않음.

## 3. 컴포넌트 구조

```
src/views/applicant/
  ApplicantInfoTabPanel.vue   ← 신설: 전체 화면 본문(breadcrumb+제목+설명+탭+칩+카드리스트) + 스타일 전담
  infoTabPanel.ts             ← 신설: 공유 타입(CardItem/Chip/PanelTab/Props) export
  ApplicantBenefits.vue       ← 재작성: 데이터 정의 → <ApplicantInfoTabPanel>
  ApplicantTraining.vue       ← 재작성: 〃
  ApplicantHrRule.vue         ← 재작성: 〃
```

- 공통 컴포넌트가 페이지 레이아웃(`.page-inner` max-width 1080, 패딩)까지 소유.
- 페이지 래퍼는 `title` / `subtitle` / `tabs` 데이터와 컴포넌트 태그 1개만 가진다. 마크업·CSS 중복 0.
- `<ApplicantBreadcrumb />`는 라우트 기반 자동 동작이라 props 불필요. 공통 컴포넌트 내부에서 1회 렌더.

## 4. 데이터 모델 (3화면 통일)

`infoTabPanel.ts`:

```ts
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
  chips: Chip[] // chips[0] = 기본 선택('전체')
}

export interface InfoTabPanelProps {
  title: string
  subtitle?: string
  tabs: PanelTab[]
}
```

### 동작
- `activeTabKey` ref: 기본값 = `tabs[0].key`.
- `activeChipKey` ref: 기본값 = 활성 탭의 `chips[0].key`. **탭이 바뀌면 첫 칩으로 리셋**.
- 현재 렌더 항목 = 활성 탭의 활성 칩 `items`.
- **열 수 규칙**: `items.length <= 7 ? 1열 : 2열` (CSS `column-count`, 세로 우선 흐름, 카드에 `break-inside: avoid`).
  - 결과: 복리후생 "복리 후생 제도/전체"(14개)만 2열, 그 외 전부 1열.
- 카테고리가 없는 탭(교육/평가/휴가)은 칩 1개(`전체`)만 → 클릭해도 항목 변화 없음(목업 그대로 장식 유지).

## 5. 화면별 데이터 (목업이 단일 출처)

문구는 목업 텍스트를 **그대로** 사용한다(기존 v1 대비 일부 축약·재서술됨).

### 5.1 복리후생 (`ApplicantBenefits.vue`)
- h1: `복리 후생 제도`
- subtitle: `임직원의 안정적인 생활과 성장을 위한 다양한 지원 제도를 안내합니다.`
- 탭: `복리 후생 제도` / `휴가 제도`

**복리 후생 제도 탭** — 칩 5개(카테고리 필터):

| 칩 | 항목(label — desc) |
|----|----|
| 전체 (14) | 아래 14개 전부 |
| 경제적 지원 (4) | 경조사 / 주택자금대출 / 선택적 복리후생 / 명절 |
| 건강과 의료 (4) | 의료비 / 치과비 / 건강검진 / 단체상해보험 |
| 여가와 문화 (4) | 동호회비 / 콘도 이용 / 안식휴가비 / 기타 복리후생 |
| 성장과 교육 (2) | 학자금 / 교육비 지원 |

전체 14개 항목(label — desc):
1. 경조사 — 경조금(결혼·사망, 본인 및 배우자 부모 칠순 등)과 화환·조화 지원
2. 학자금 — 유치원·중·고등학교, 대학교 학자금 지급
3. 의료비 — 본인 및 건강보험증 등재 가족에게 연간 1,000만원 한도 지원
4. 치과비 — 본인이 사용한 치과진료비를 5년간 100만원 한도 지원
5. 동호회비 — 축구·독서 등 사내 동호회 활동 경비 지원
6. 안식휴가비 — 5년 100만원, 10년 200만원, 15년 이상 매 5년 400만원
7. 주택자금대출 — 직원 전세자금 및 주택 구입자금 대출 지원
8. 선택적 복리후생 — 개인별 부여 포인트 내에서 여가활동 등 비용 지원
9. 건강검진 — 격년 주기로 종합건강검진 실시
10. 명절 — 경로효친비 및 명절 선물 지원
11. 콘도 이용 — 전국 각지의 회사 보유 콘도 이용 지원
12. 기타 복리후생 — 가을행사 기념품, 장기근속자 포상 등 지원
13. 교육비 지원 — 직무관련 교육 이수 및 자격증 취득 경비 지원
14. 단체상해보험 — 임직원 단체상해보험 가입 지원

> "전체" 칩은 위 1~14 원래 순서대로, 카테고리 칩은 위 표의 순서대로 렌더(목업 `benefitGroups` 인덱스 매핑과 동일).

**휴가 제도 탭** — 칩 1개(전체, 4):
- 정기휴가 — 유급휴가 연 5일
- 법정휴가 — 근로기준법에 따른 연차휴가
- 경조휴가·공가 — 각종 경조사 휴가 및 공가
- 안식휴가 — 5년 4일, 10년 7일, 15년 이상 매 5년 10일

### 5.2 교육 (`ApplicantTraining.vue`)
- h1: `교육 제도`
- subtitle: `임직원의 전문성과 성장을 지원하는 사내·외 교육 과정을 안내합니다.`
- 탭: `사내교육` / `사외교육` / `법정의무교육` (각 칩 1개=전체)

**사내교육 (4)**
- 직무교육 — 자산관리전문가 · 고객업무전문가 · 실무교육
- 직급교육 — 신입사원 입문교육 · 수시입사자 안내 · Follow-up 과정 · 승진 자격과정 · 리더십과정
- 자격관리교육 — 자격시험 대비과정 · 투자권유자문인력 투자자보호
- ADVANCED 교육 — 고급관계관리(ARM) · 고급정보기술(AIT)

**사외교육 (3)**
- 금융투자교육원 주관 교육 — 전문교육 · 집합교육 · 온라인교육
- 기타 외부기관 주관 교육 — 영업 · 운용 · IB · 리서치 · IT · 지원 · 준법/리스크/정보보호 · 공통
- 교육 신청절차 — 금융투자교육원 교육 신청 · 기타 외부기관 주관 교육 신청

**법정의무교육 (3)**
- 직장 내 성희롱 예방교육 — 근거: 남녀고용평등과 일·가정 양립 지원에 관한 법률
- 금융정보보호교육 — 근거: 전자금융감독규정 제19조 2항
- 금융투자전문인력 보수교육 — 근거: 금융투자전문인력과 자격시험에 관한 규정 제5-2조 4항

### 5.3 평가 (`ApplicantHrRule.vue`)
- h1: `보상 및 평가`
- subtitle: `역량과 성과에 기반한 보상 체계와 공정한 평가 제도를 안내합니다.`
- 탭: `보상제도` / `평가제도` (각 칩 1개=전체)

**보상제도 (2)**
- 연봉제 시행 — 직원 개개인의 역량과 성과에 근거한 연봉제 시행
- 성과급 지급 — 직원 개개인의 종합평가 결과에 근거한 성과급 지급

**평가제도 (2)**
- 평가그룹별 평가 시행 — Assistant: 신입사원에 한해 입사 후 일정기간 Career path 탐색 기회 부여 · Manager: 역량 및 성과에 따른 종합평가
- 평가방법 — 역량평가: 평가그룹별 assign 된 역량 평가 · 성과평가: 설정 목표 대비 달성도 평가 · 종합평가: 역량·성과를 종합해 승진·전보·연봉을 합리적으로 결정

## 6. UI / 레이아웃 명세 (목업 충실 재현)

치수·색은 목업의 인라인 스타일 기준. 색상은 §7 토큰으로 치환.

### 페이지
- 페이지 배경: `#ffffff` (각 화면이 독립된 흰 서피스. 목업의 회색 갭+스택은 목업 프레젠테이션용이므로 제외).
- `.page-inner`: max-width 1080px, margin 0 auto, padding `42px 20px 80px` (평가 화면은 하단 88px).
- 좌상단 `화면 · 복리후생` 같은 작은 주석 라벨: **제외**.

### 헤더
- `<ApplicantBreadcrumb />` (기존 컴포넌트, 라우트 기반).
- h1: margin `18px 0 0`, font-size 40px(복리후생) / 38px(교육·평가), weight 800, line-height 1.25, letter-spacing -0.04em, color `--tap-text`.
- subtitle `<p>`: margin `9px 0 0`, font-size 15px, line-height 1.6, color `--app-text-muted`, letter-spacing -0.02em.

### 탭 바
- 컨테이너: `display:flex; align-items:flex-end; margin-top:34px;`
- 탭 버튼 공통: min-width 230px, height 58px, padding `0 30px`, inline-flex center, font-size 18px, letter-spacing -0.02em, border-radius `8px 8px 0 0`, cursor pointer, transition `color .18s, background-color .18s`.
  - 활성: color `--app-color-primary-hover`, weight 700, border `2px solid --app-color-primary-hover`, border-bottom 0, bg #fff.
  - 비활성: color `--app-text-muted`, weight 500, border 0, border-bottom `2px solid --app-color-primary-hover`, bg `--tap-muted-soft`.
- 채움 라인: `flex:1; min-width:0; height:58px; border-bottom:2px solid --app-color-primary-hover;`

### 패널
- border `2px solid --app-color-primary-hover`, border-top 0, border-radius `0 0 8px 8px`, bg #fff, box-shadow `0 10px 28px --tap-panel-shadow`.
- 내부 패딩: `34px 44px 42px`.

### 칩 행
- `display:flex; flex-wrap:wrap; gap:8px; margin-bottom:26px;`
- 칩 버튼 공통: inline-flex, align center, gap 6px, padding `8px 15px`, border-radius 20px, font-size 13.5px, weight 600, letter-spacing -0.01em, cursor pointer, transition `all .15s`.
  - 활성: bg `--app-color-primary`, color #fff, border `1px solid --app-color-primary`.
  - 비활성: bg #fff, color `--tap-chip-text`, border `1px solid --tap-chip-border`.
- 개수 배지 `<span>`: font-size 11.5px, weight 700, color 활성 `rgba(255,255,255,0.7)` / 비활성 `--tap-chip-count`.

### 카드 리스트
- 리스트 컨테이너: `column-count: 1|2; column-gap: 30px;` (§4 규칙).
- 카드: `display:flex; gap:13px; break-inside:avoid; padding:14px 4px; border-bottom:1px solid --tap-card-divider;`
  - 좌측 강조바: `flex-shrink:0; width:3px; align-self:stretch; background:--tap-accent-bar; border-radius:2px;`
  - 라벨: font-size 15px, weight 700, color `--app-text-primary`, letter-spacing -0.01em.
  - 설명: margin-top 5px, font-size 13px, line-height 1.6, color `--app-text-secondary`, letter-spacing -0.02em, `word-break:keep-all; text-wrap:pretty;` (desc 없으면 미렌더).

## 7. 색상 토큰

### 기존 변수 재사용 (variables.scss에 이미 존재)
| 목업 값 | 토큰 |
|----|----|
| `#0f4726` | `--app-color-primary` |
| `#146135` | `--app-color-primary-hover` |
| `#202a32` | `--tap-text` |
| `#1f2937` | `--app-text-primary` |
| `#6b7280` | `--app-text-secondary` |
| `#9ca3af` | `--app-text-muted` |
| `#f9f9f9` | `--tap-muted-soft` |
| `rgba(31,41,55,0.06)` | `--tap-panel-shadow` |

### 신규 추가 토큰 (variables.scss `--tap-*` 블록에 append, 기존 값 수정 없음)
```scss
--tap-accent-bar: #cfe0c8;    /* 카드 좌측 강조바 */
--tap-card-divider: #eef1ee;  /* 카드 하단 구분선 */
--tap-chip-border: #dfe5dc;   /* 비활성 칩 테두리 */
--tap-chip-text: #5b6b5f;     /* 비활성 칩 글씨 */
--tap-chip-count: #9aa896;    /* 비활성 칩 개수 배지 */
```

## 8. 반응형 (≤ 768px)

공통 컴포넌트 scoped CSS의 미디어 쿼리로 처리:
- `.page-inner` 패딩 축소(예: `32px 16px 64px`), 패널 패딩 축소(예: `26px 22px`).
- h1 폰트 축소(예: 30px).
- 탭: `flex:1; min-width:0;` 균등 분배, height/padding/font 축소, 채움 라인 `display:none`.
- 카드 리스트: `column-count: 1` 강제.
- 칩: 기본 `flex-wrap` 으로 줄바꿈.

## 9. 구현 순서 (계획 단계 참고)

1. `variables.scss`: 액센트 토큰 5종 추가.
2. `infoTabPanel.ts`: 타입 정의.
3. `ApplicantInfoTabPanel.vue`: 공통 컴포넌트(템플릿+상태+스타일+반응형).
4. `ApplicantBenefits.vue` 재작성(카테고리 칩 데이터 포함).
5. `ApplicantTraining.vue` 재작성.
6. `ApplicantHrRule.vue` 재작성.
7. 검증.

## 10. 검증

- `cd recruit_front && npm run type-check` (필요 시 `npm run build`).
- 백엔드 테스트 없음. `api-contract.md` 변경 없음(계약 영향 없음).
- 수동 확인: 각 화면 탭 전환, 복리후생 카테고리 칩 필터링, 14개 항목 2열/그 외 1열, 모바일 1열.

## 11. 확정된 결정 / 가정

1. 공통 컴포넌트 이름: **`ApplicantInfoTabPanel.vue`** (`src/views/applicant/`).
2. 액센트 색 5종: **`variables.scss`의 `--tap-*` 블록에 추가**(scoped 로컬 아님 → 단일 출처 테마).
3. 교육·평가의 단일 "전체" 칩: **목업대로 유지**(장식용, 비인터랙티브).
4. 문구: 목업 텍스트 그대로(v1 문구 폐기). 한글 UI 텍스트 유지(AGENTS.md 준수).
5. 브레드크럼: 목업 하드코딩 대신 기존 `<ApplicantBreadcrumb />` 사용.
