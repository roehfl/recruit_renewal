# 통합 하네스 설계서 (front + back)

- 날짜: 2026-06-23
- 상태: 승인됨 (구현 계획 작성 전)
- 대상: 신영증권 채용 Renewal — 프론트(Vue.js) + 백엔드(Spring Boot) 통합 에이전트 하네스

## 1. 배경

채용 프로젝트는 독립된 두 git 저장소로 구성된다.

- 백엔드: `recruit_back/recruit_backend/` — Spring Boot, 자체 `.git` (`github.com/roehfl/recruit_backend`, branch `main`)
- 프론트: `recruit_front/` — Vue 3 + Vite + TS, 자체 `.git` (`github.com/roehfl/recruit.git`, branch `main`)

두 저장소는 각자 이미 하네스를 가지고 있다.

- 백엔드 `CLAUDE.md`: 매우 상세. Phase/Slice 단위 vertical slice 개발, `docs/codex/01~07` 필독 문서, 패키지 구조/스타일 규칙, 보안 금지사항, Markdown+HTML 이중 문서화, 보고 형식.
- 프론트 `AGENTS.md`: Vue3+Vite+TS+Pinia+Axios+ant-design-vue, 기존 소스 보존, `VITE_API_BASE_URL` 사용, 한글 UI 텍스트 보존.

지금까지 백엔드 작업은 백엔드 저장소 안에서만 이루어졌고, 프론트는 별도로 진행되어 왔다. 이제 프론트 개발이 진행되면서 **API/화면 변경 시 양쪽을 함께 고려**해야 하는 상황이 되었다.

### 발견된 위험 (반드시 처리)

상위 `recruit/` 폴더가 홈 디렉토리(`C:/Users/roehf`)에 우발적으로 생성된 git 저장소(`fit.git`, branch `sol`)에 딸려 들어가 있다. recruit/에서 무심코 `git add -A` 등을 실행하면 홈 전체가 섞일 수 있는 footgun이 존재한다.

## 2. 목표 / 비목표

### 목표

- 프론트/백을 아우르는 **가벼운 통합 하네스**를 상위 `recruit/` 폴더에 둔다.
- API/화면 변경 시 **단일 세션에서 양쪽을 함께 수정**하고, **API 계약 문서로 동기화**하여 한쪽 변경 누락을 막는다.
- 백엔드는 **수정한 패키지 테스트만** 수행하고, 전체 리그레션은 돌리지 않는다.
- 기존 두 저장소의 하네스를 **그대로 보존**하고, 통합 진입 문서가 이를 오케스트레이션한다.
- 홈 `fit.git` footgun을 차단한다.

### 비목표

- 백엔드/프론트 하네스의 대규모 재작성 또는 병합.
- OpenAPI/Swagger 등 무거운 계약 자동화 도입.
- 슬래시 커맨드/서브에이전트/hooks 등 자동화(이번 범위 아님; 향후 확장 가능).
- 백엔드 전체 리그레션 테스트, 프론트 전체 테스트 스위트 강제.

## 3. 핵심 결정 사항 (브레인스토밍 합의)

| # | 주제 | 결정 |
|---|------|------|
| 1 | 하네스 위치 | 상위 `recruit/` 폴더 |
| 2 | 조정 방식 | 단일 세션에서 양쪽 함께 수정 + API 계약 문서 동기화 |
| 3 | 구성 범위 | 가이드 문서 중심(가벼움), 자동화는 권한 설정 수준 최소 |
| 4 | 계약 문서 | 가벼운 단일 `api-contract.md` |
| 5 | 검증 정책 | 백: 수정 패키지 테스트 / 프론트: type-check (+필요시 build) |
| 6 | git 구성 | `recruit/`를 로컬 전용 git으로 init (원격 없음) |
| 7 | 문서 아키텍처 | 얇은 오케스트레이터 + 위임 (접근 1) |

## 4. 아키텍처 — 파일 레이아웃

신규 산출물은 `recruit/` 루트의 두 파일과 git 설정뿐이며, 기존 두 저장소의 하네스는 무수정으로 유지한다.

```text
recruit/
├─ .git/                            # [신규] 로컬 전용 하네스 저장소 (원격 없음)
├─ .gitignore                       # [신규] recruit_back/, recruit_front/, node_modules 등 제외
├─ CLAUDE.md                        # [신규] 통합 진입 오케스트레이터 (얇음)
├─ api-contract.md                  # [신규] 가벼운 단일 API 계약 문서
├─ docs/superpowers/specs/          # [신규] 본 설계서 위치
├─ recruit_back/recruit_backend/    # [기존] 자체 .git · CLAUDE.md · docs/codex 유지
└─ recruit_front/                   # [기존] 자체 .git · AGENTS.md 유지
```

### 작동 방식 (Claude Code CLAUDE.md 자동 로딩 활용)

- 작업은 `recruit/`에서 시작 → `recruit/CLAUDE.md`(오케스트레이터)가 먼저 로드된다.
- 백엔드 하위로 진입해 작업하면 상위 `recruit/CLAUDE.md` + 백엔드 `CLAUDE.md`가 함께 로드된다(부모 디렉토리 자동 로딩).
- 프론트 규칙은 `AGENTS.md`에 있고 Claude Code는 이를 기본 자동 로드하지 않으므로, 오케스트레이터가 "프론트 작업 시 `recruit_front/AGENTS.md`를 권위 문서로 읽으라"고 명시해 다리를 놓는다.

### `recruit/CLAUDE.md`(오케스트레이터) 구성 — 얇게 유지

1. 프로젝트 개요 & 두 저장소 위치/역할
2. 읽기 순서 (작업 유형별 필독 문서)
3. 화면 슬라이스 워크플로우 (§5)
4. API 계약 문서 동기화 규약 (§6)
5. 검증 정책 (§7)
6. git 안전 규칙 (§8)
7. 기존 하네스 충돌 정리/override (§9)
8. 위임 선언: 레포별 상세는 각 repo의 `CLAUDE.md` / `AGENTS.md`를 따름

## 5. 화면 슬라이스 워크플로우 (cross-cutting 핵심)

작업의 기본 단위 = **하나의 화면 슬라이스**. 사용자가 "X 화면 개발" 또는 "Y API 변경"을 지시하면 에이전트는 한 세션에서 다음 순서로 진행한다.

0. **범위 고정** — 사용자가 지정한 화면/API만. 다른 화면으로 범위 확장 금지(백엔드의 "요청 범위 밖 리팩터링 금지" 원칙과 동일). 대상 화면·연관 엔드포인트 식별, `api-contract.md`에서 기존 계약 확인.
1. **계약 정렬 (먼저)** — 변경될 엔드포인트의 요청/응답 스키마를 먼저 정리해 `api-contract.md`에 🟡 초안으로 반영. 양쪽 구현의 기준점.
2. **백엔드 구현** — 해당 패키지에 controller/service/DTO 등 추가·수정. 백엔드 `CLAUDE.md` 규칙 준수(`ApiResponse<T>`, record DTO, 패키지 구조, 보안 금지사항 등).
3. **백엔드 검증** — 수정한 패키지 테스트만 실행. 전체 리그레션 금지.
4. **프론트 구현** — `src/api` 모듈을 계약에 맞추고, 대상 화면(view/component)·store·route 구현. `AGENTS.md` 규칙 준수(ant-design-vue, `VITE_API_BASE_URL`, 한글 UI 보존 등).
5. **프론트 검증** — `npm run type-check` (+ 필요시 `build`).
6. **계약 확정** — 실제 구현과 `api-contract.md`를 일치시켜 🟢로 최종 갱신(불일치 시 문서를 코드에 맞춤).
7. **보고** — 양쪽 변경 파일 / 테스트 결과 / 계약 변경분 / 남은 이슈 요약.

### 방향 규칙

- 기본 순서는 **백엔드 → 프론트**(프론트가 응답 스키마에 의존).
- 계약 문서가 단일 기준이므로 순서가 바뀌어도 동기화는 유지된다.

### 한쪽만 바뀔 때

- 순수 프론트 변경(예: 레이아웃)으로 API 무변경이면 2·3단계 생략하되 "계약 영향 없음"을 확인한다.
- 순수 백엔드 변경도 대칭적으로 처리한다.

## 6. `api-contract.md` 포맷

화면 단위로 구성하고, 각 화면 아래 엔드포인트를 나열한다. 전체 타입을 정의하는 무거운 스펙이 아니라 **양쪽을 잇는 요약 + 매핑**만 담는 가벼운 인덱스다. 상세 타입은 백엔드 DTO/`docs/codex`에 위임한다.

````markdown
# API 계약 문서 (recruit)

front-back 동기화의 단일 기준. 슬라이스 작업 시 구현 전 초안 갱신 → 구현 후 최종 확정.
상태 표기: 🟢 구현됨 · 🟡 초안(구현중) · 🔴 변경필요
※ 변경 이력은 이 파일의 git 로그로 대체(별도 changelog 불필요).

## 화면: 로그인 (LoginView)
- 프론트: `src/views/auth/LoginView.vue`, `src/api/auth.ts`
- 백엔드: `com.shinyoung.recruit.controller.AuthController`

### POST /auth/login  🟢
- 설명: 지원자/임직원 로그인 (세션 기반)
- 요청: `{ username, password }`
- 응답(200): `ApiResponse<{ userId, name, role }>`
- 오류: 401 인증 실패
- 매핑: front `authApi.login()` ↔ back `AuthController.login()`
````

### 운영 규칙 (오케스트레이터에 명시)

- 작업 시작 시 대상 화면 섹션을 읽고, 없으면 새로 추가한다.
- 구현 전 🟡 초안으로 요청/응답 스키마 기재 → 구현 후 🟢로 확정하며 코드와 일치 검증.
- 요청/응답은 필드 모양 요약 수준(정확한 타입·검증은 백엔드 DTO가 단일 출처).
- 한 엔드포인트가 여러 화면에서 쓰이면 한 곳에 정의하고 다른 화면에서 참조한다.

## 7. 검증 정책 (정확한 명령어)

### 백엔드 — `recruit_back/recruit_backend/`에서, 수정 패키지만

```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md의 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.<수정패키지>.*" --no-daemon
```

### 프론트 — `recruit_front/`에서

```bash
npm run type-check          # vue-tsc (기본)
npm run build               # 필요시 (type-check 포함 상위 검증; build = run-p type-check + build-only)
```

### 공통 규칙

- 전체 리그레션(`gradlew test` 전체)·전체 빌드는 명시 요청 시에만 수행한다.
- 실제 운영 AES 키·LDAP·DB 접속정보는 절대 사용/기재하지 않는다(예시 값만).
- 프론트 단위 테스트(`npm run test:unit`, vitest)는 기본 정책에 포함하지 않으며 필요 시에만 실행한다.

## 8. git 구성

```bash
cd recruit && git init      # 로컬 전용, 원격 없음
```

`.gitignore`:

```gitignore
recruit_back/
recruit_front/
node_modules/
*.log
.DS_Store
```

- 추적 대상은 `CLAUDE.md`, `api-contract.md`, `.gitignore`, `docs/`뿐. 두 코드 저장소는 각자 관리한다.
- `recruit/.git`이 홈 `fit.git`보다 가까운 조상이므로, recruit/ 안의 모든 경로는 recruit 로컬 저장소 또는 각 sub-repo가 담당한다 → **홈 fit.git footgun 차단**.
- 잔여 주의: 홈 디렉토리에서 직접 git 명령을 실행할 때는 여전히 fit.git이 대상이 되므로 주의한다.

## 9. 기존 하네스 충돌/연결 정리 (오케스트레이터에 명시)

- **백엔드 `CLAUDE.md` line 7**("프론트엔드는 별도 프로젝트, 이 저장소는 백엔드만, Vue/정적 리소스 생성 금지"): 폐기가 아니라 **명확화**한다 — "백엔드 *저장소 안에는* 여전히 프론트 코드를 만들지 않는다. 프론트 변경은 *프론트 저장소에서* 수행한다." 실질 충돌이 아니다.
- **백엔드 이중 문서화(Markdown + HTML 리포트)**: 백엔드 단독 Phase 작업은 기존 규칙을 유지한다. 통합 화면 슬라이스에서는 `api-contract.md` 갱신 + 보고 요약으로 충분하며, 슬라이스마다 HTML 리포트를 강제하지 않는다. 백엔드 변경이 기존 Phase 문서 체계에 해당하면 그때 그 규칙을 따른다.
- **프론트 규칙 연결**: 오케스트레이터가 "프론트 작업 시 `recruit_front/AGENTS.md`를 권위 문서로 읽으라"고 명시한다(프론트 저장소는 무수정).
- **언어**: 오케스트레이터(`recruit/CLAUDE.md`)는 한국어, 프론트 `AGENTS.md`는 영문 원본을 유지한다.

## 10. 구현 산출물 (writing-plans 단계에서 상세화)

1. `recruit/` git init (로컬 전용) + `.gitignore` 생성.
2. `recruit/CLAUDE.md` 오케스트레이터 작성 (§4.3 구성).
3. `recruit/api-contract.md` 초기 골격 작성 (§6 포맷). 현재 개발 중인 화면(지원자/인증)부터 시드 가능.
4. 본 설계서 커밋.

## 11. 향후 확장 (이번 범위 밖)

- 자주 쓰는 화면 슬라이스 워크플로우를 슬래시 커맨드(예: `/screen-slice`)로 자동화.
- 백/프론트 전용 서브에이전트 분리.
- hooks 기반 테스트 자동 실행/계약 검증.
- 필요 시 OpenAPI 도입으로 계약 자동 생성.
