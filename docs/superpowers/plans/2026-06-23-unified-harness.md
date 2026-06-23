# 통합 하네스 (front + back) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채용 프론트(Vue)/백엔드(Spring Boot)를 아우르는 가벼운 통합 하네스를 `recruit/`에 만든다 — 얇은 오케스트레이터 `CLAUDE.md`와 단일 `api-contract.md`.

**Architecture:** 접근 1(얇은 오케스트레이터 + 위임). `recruit/CLAUDE.md`는 cross-cutting 규칙(워크플로우/계약/검증/git/충돌정리)만 담고, 레포별 상세는 기존 `recruit_back/recruit_backend/CLAUDE.md`·`recruit_front/AGENTS.md`에 위임한다. 기존 두 저장소는 무수정.

**Tech Stack:** Markdown(문서 산출물), git(로컬 전용 recruit 저장소), 검증 명령은 Gradle(백)·npm/vue-tsc(프론트).

> **참고 — 이 계획의 특성:** 산출물은 코드가 아니라 **문서(하네스)**다. 단위 테스트 프레임워크가 없으므로, 각 Task의 "verify" 단계는 *참조 사실(경로·명령·스크립트)이 실재하는지 확인*하고 *문서가 발명된 계약을 담지 않는지 확인*하는 것으로 대체한다. "create → verify → commit" 흐름은 유지한다.

> **선행 완료(이미 됨):** `recruit/` 로컬 git init, `.gitignore`, 설계서 커밋(`2262c53`). 이 계획은 그 위에 이어서 진행한다.

---

## File Structure

| 파일 | 책임 | 비고 |
|------|------|------|
| `recruit/CLAUDE.md` | 통합 진입 오케스트레이터. 구성/읽기순서/워크플로우/계약규약/검증/git/충돌정리/위임. | 신규, 한국어, 얇게 |
| `recruit/api-contract.md` | front-back API 계약의 단일 기준. 헤더/범례/규칙/템플릿. | 신규, 초기엔 템플릿만(발명 금지) |
| `recruit/.gitignore` | 코드 저장소·node_modules 제외 | **이미 생성됨** (수정 불필요) |
| `recruit/docs/superpowers/specs/2026-06-23-unified-harness-design.md` | 설계서 | **이미 커밋됨** |

기존 무수정 유지: `recruit_back/recruit_backend/CLAUDE.md`, `recruit_back/recruit_backend/docs/codex/`, `recruit_front/AGENTS.md`.

---

## Task 1: `recruit/CLAUDE.md` 오케스트레이터 작성

**Files:**
- Create: `recruit/CLAUDE.md`

- [ ] **Step 1: 참조 사실 검증 (문서가 가리킬 경로·명령이 실재하는지 확인)**

Run (작업 루트 `recruit/`에서):
```bash
ls CLAUDE.md 2>/dev/null && echo "[ALREADY EXISTS - STOP]" || echo "[OK: not yet created]"
ls recruit_back/recruit_backend/CLAUDE.md
ls -d recruit_back/recruit_backend/docs/codex
ls recruit_back/recruit_backend/gradlew.bat
ls -d recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit
ls recruit_front/AGENTS.md
grep -E '"(type-check|build)"' recruit_front/package.json
```
Expected:
- `CLAUDE.md` → `[OK: not yet created]`
- 나머지 모든 경로 존재(에러 없이 출력됨)
- grep 결과에 `"type-check"`와 `"build"` 스크립트 라인이 보임

만약 `recruit/CLAUDE.md`가 이미 있으면 중단하고 사용자에게 알린다(덮어쓰기 금지).

- [ ] **Step 2: `recruit/CLAUDE.md` 생성 (아래 전체 내용 그대로)**

````markdown
# CLAUDE.md — 통합 하네스 (채용 front + back)

이 폴더(`recruit/`)는 신영증권 채용 Renewal의 **프론트엔드 + 백엔드를 함께 다루는 작업 루트**다.
이 문서는 얇은 **오케스트레이터**다. 레포별 상세 규칙은 각 저장소의 문서에 위임한다.

## 1. 구성

- 백엔드: `recruit_back/recruit_backend/` — Spring Boot. 자체 git. 권위 문서: `recruit_back/recruit_backend/CLAUDE.md` + `docs/codex/`.
- 프론트: `recruit_front/` — Vue 3 + Vite + TS. 자체 git. 권위 문서: `recruit_front/AGENTS.md`.
- 계약 문서: `recruit/api-contract.md` — front-back API 동기화의 단일 기준.

## 2. 읽기 순서

작업 유형에 따라 먼저 읽는다.

- 모든 작업: 이 문서(`recruit/CLAUDE.md`) → `recruit/api-contract.md`의 대상 화면 섹션.
- 백엔드를 건드리면: `recruit_back/recruit_backend/CLAUDE.md`(및 거기서 지시하는 `docs/codex/01~04`).
- 프론트를 건드리면: `recruit_front/AGENTS.md`.

Claude Code는 부모 디렉토리의 `CLAUDE.md`를 자동 로드하므로, 백엔드 하위에서 작업하면 이 문서와 백엔드 `CLAUDE.md`가 함께 적용된다. 프론트 규칙(`AGENTS.md`)은 자동 로드되지 않으므로 **프론트 작업 시 반드시 `recruit_front/AGENTS.md`를 읽는다.**

## 3. 화면 슬라이스 워크플로우 (핵심)

작업 단위는 **하나의 화면 슬라이스**다. 사용자가 지정한 화면/API만 다루고, 다른 화면으로 범위를 넓히지 않는다.

0. 범위 고정: 대상 화면·연관 엔드포인트 식별. `api-contract.md`에서 기존 계약 확인.
1. 계약 정렬(먼저): 변경될 엔드포인트의 요청/응답 스키마를 `api-contract.md`에 🟡 초안으로 기재.
2. 백엔드 구현: 해당 패키지에 controller/service/DTO 등 추가·수정(백엔드 `CLAUDE.md` 규칙 준수).
3. 백엔드 검증: 수정한 패키지 테스트만 실행(§5).
4. 프론트 구현: `src/api` 모듈을 계약에 맞추고 대상 화면·store·route 구현(`AGENTS.md` 규칙 준수).
5. 프론트 검증: `npm run type-check`(+필요시 `build`).
6. 계약 확정: 실제 구현과 `api-contract.md`를 일치시켜 🟢로 갱신.
7. 보고: 양쪽 변경 파일 / 테스트 결과 / 계약 변경분 / 남은 이슈 요약.

방향: 기본 **백엔드 → 프론트**(프론트가 응답 스키마에 의존). 계약 문서가 기준이므로 순서가 바뀌어도 동기화는 유지된다.
한쪽만 변경: API 무변경 프론트 작업은 2·3 생략하되 "계약 영향 없음"을 확인한다. 백엔드 단독도 대칭으로 처리한다.

## 4. API 계약 문서 동기화 규약

- `recruit/api-contract.md`가 front-back 계약의 **단일 기준**이다.
- 슬라이스 시작 시 대상 화면 섹션을 읽고, 없으면 추가한다.
- 구현 전 🟡 초안 → 구현 후 🟢 확정(코드와 일치 검증).
- 요청/응답은 **필드 모양 요약** 수준만 적는다. 정확한 타입·검증은 백엔드 DTO가 단일 출처다.
- 계약을 임의로 발명하지 않는다. 불명확하면 🔴로 표시하고 사용자에게 확인한다.

## 5. 검증 정책

전체 리그레션/전체 빌드는 **명시 요청 시에만**. 평소엔 변경 범위만 검증한다.

백엔드(`recruit_back/recruit_backend/`에서, 수정 패키지만):

```powershell
$env:AES_SECRET_KEY='<백엔드 CLAUDE.md의 로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.<수정패키지>.*" --no-daemon
```

프론트(`recruit_front/`에서):

```bash
npm run type-check     # 기본 (vue-tsc)
npm run build          # 필요시 (type-check 포함)
```

실제 운영 AES 키·LDAP·DB 접속정보는 절대 사용/기재하지 않는다(예시 값만). 프론트 단위 테스트(`npm run test:unit`)는 필요 시에만.

## 6. git 안전 규칙

- `recruit/`는 **로컬 전용** git 저장소다(원격 없음). 통합 하네스 문서만 추적한다.
- `recruit_back/`, `recruit_front/`는 **각자 자체 git**으로 관리한다. recruit/ 저장소는 이들을 `.gitignore`로 제외한다.
- 백엔드/프론트 변경은 각 저장소 안에서 커밋한다(해당 저장소의 git 규칙 준수).
- 명확한 요청 없이 `git commit`/`git push`/브랜치 조작을 하지 않는다.
- 홈 디렉토리(`C:/Users/roehf`)에는 별개의 `fit.git`이 있다. recruit/ 안에서는 recruit 로컬 저장소가 우선되지만, **홈에서 직접 git 명령을 실행하지 않는다.**

## 7. 기존 하네스와의 관계 (override/명확화)

- 백엔드 `CLAUDE.md`의 "이 저장소는 백엔드만, Vue/정적 리소스 생성 금지"는 유효하다. 의미는 **"백엔드 저장소 안에 프론트 코드를 만들지 않는다"**이며, 프론트 변경은 `recruit_front/`에서 수행한다.
- 백엔드의 Markdown+HTML 이중 문서화는 **백엔드 단독 Phase 작업**에만 적용한다. 통합 화면 슬라이스는 `api-contract.md` 갱신 + 보고 요약으로 충분하다.
- 충돌 시: 통합/조정 규칙은 이 문서가, 레포 내부 구현 규칙은 각 레포 문서가 우선한다.

## 8. 위임

레포별 상세(패키지 구조, 스타일, 보안 금지사항, 도메인 설계, 프론트 라이브러리 규칙 등)는 각 저장소 문서를 단일 출처로 따른다.

- 백엔드: `recruit_back/recruit_backend/CLAUDE.md`
- 프론트: `recruit_front/AGENTS.md`
````

- [ ] **Step 3: 생성 후 검증 (참조 무결성)**

Run (작업 루트 `recruit/`에서):
```bash
ls CLAUDE.md
echo "--- CLAUDE.md가 참조하는 경로가 모두 실재하는지 ---"
for p in recruit_back/recruit_backend/CLAUDE.md recruit_back/recruit_backend/docs/codex recruit_front/AGENTS.md recruit_back/recruit_backend/gradlew.bat; do
  ls -d "$p" >/dev/null 2>&1 && echo "OK  $p" || echo "MISSING  $p"
done
```
Expected: `CLAUDE.md` 존재 + 네 경로 모두 `OK`. `MISSING`이 하나라도 있으면 문서를 수정한다.

- [ ] **Step 4: 커밋**

```bash
git add CLAUDE.md
git commit -m "feat: 통합 하네스 오케스트레이터 CLAUDE.md 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: `recruit/api-contract.md` 작성

**Files:**
- Create: `recruit/api-contract.md`

> **발명 금지 원칙:** 초기 `api-contract.md`는 헤더/범례/규칙 + **템플릿만** 담는다. 실제 화면 계약(요청/응답 스키마)은 실제 슬라이스 작업 시 코드를 확인하고 채운다. 템플릿의 파일 경로 예시(`LoginView.vue`, `authApi.ts`, `AuthController`)는 실재 파일이지만, 요청/응답은 플레이스홀더로 두어 검증되지 않은 계약을 단정하지 않는다.

- [ ] **Step 1: 사전 확인 (파일 미존재 + 템플릿 경로 실재 확인)**

Run (작업 루트 `recruit/`에서):
```bash
ls api-contract.md 2>/dev/null && echo "[ALREADY EXISTS - STOP]" || echo "[OK: not yet created]"
ls recruit_front/src/api
ls recruit_front/src/views/auth/LoginView.vue
ls recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/controller/AuthController.java
```
Expected:
- `api-contract.md` → `[OK: not yet created]`
- `src/api`에 `authApi.ts` 등 모듈이 보임
- `LoginView.vue`, `AuthController.java` 존재(템플릿 예시 경로가 실재함을 확인)

이미 존재하면 중단하고 사용자에게 알린다.

- [ ] **Step 2: `recruit/api-contract.md` 생성 (아래 전체 내용 그대로)**

````markdown
# API 계약 문서 (recruit)

front-back 동기화의 **단일 기준**. 화면 슬라이스 작업 시 구현 전 🟡 초안으로 기재하고, 구현 후 🟢로 확정한다.

## 상태 표기

- 🟢 구현됨 (front-back 양쪽 구현·검증 완료)
- 🟡 초안 (구현 중)
- 🔴 변경필요 / 불명확 (사용자 확인 필요)

## 운영 규칙

- 화면 단위로 섹션을 만들고, 각 화면 아래 엔드포인트를 나열한다.
- 요청/응답은 **필드 모양 요약** 수준만 적는다. 정확한 타입·검증 규칙은 백엔드 DTO가 단일 출처다.
- 계약을 임의로 발명하지 않는다. 불명확하면 🔴로 표시하고 사용자에게 확인한다.
- 한 엔드포인트가 여러 화면에서 쓰이면 한 곳에 정의하고 다른 화면에서 참조한다.
- 변경 이력은 이 파일의 git 로그로 대체한다(별도 changelog 불필요).

---

## 템플릿 (복사해서 사용 — 아래는 형식 예시이며 실제 계약 아님)

### 화면: <화면명> (<ViewComponent>)

- 프론트: `src/views/.../<View>.vue`, `src/api/<module>.ts`
- 백엔드: `com.shinyoung.recruit.controller.<Controller>`

#### <METHOD> <경로>  🟡

- 설명: <한 줄>
- 요청: `{ ... }`
- 응답(200): `ApiResponse<{ ... }>`
- 오류: <코드/사유>
- 매핑: front `<api 함수>()` ↔ back `<Controller>.<method>()`

> 형식 참고용 예시(실제 검증 전 계약 아님):
> 화면: 로그인 (LoginView) — 프론트 `src/views/auth/LoginView.vue`, `src/api/authApi.ts` / 백엔드 `AuthController`
> `POST /auth/login` → 요청 `{ username, password }`, 응답 `ApiResponse<{ ... }>`, 매핑 front `authApi.login()` ↔ back `AuthController.login()`

---

## 화면 계약

> 실제 화면 계약은 슬라이스 작업 시 위 템플릿을 복사해 이 아래에 추가한다.
````

- [ ] **Step 3: 생성 후 검증**

Run (작업 루트 `recruit/`에서):
```bash
ls api-contract.md
echo "--- 발명된 단정 계약이 없는지(상태가 🟢인 실제 엔드포인트가 아직 없어야 함) ---"
grep -c "🟢" api-contract.md
grep -n "## 화면 계약" api-contract.md
```
Expected: `api-contract.md` 존재. `🟢` 개수는 `0`(범례 설명 줄은 이모지가 라벨로만 쓰임 — 실제 계약 엔트리는 없음). "## 화면 계약" 섹션이 존재하고 그 아래 실제 엔트리는 비어 있음.

> 참고: 범례 줄 "🟢 구현됨 ..."은 설명이지 계약 엔트리가 아니다. grep 카운트가 0이 아니라 1(범례)로 나오면 정상이다. 핵심은 "## 화면 계약" 아래에 단정된 실제 엔드포인트가 없다는 것.

- [ ] **Step 4: 커밋**

```bash
git add api-contract.md
git commit -m "feat: API 계약 문서 api-contract.md 골격 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 통합 점검 (검증 전용, 커밋 없음)

**Files:** 없음 (검증만)

- [ ] **Step 1: 하네스 일관성 확인**

Run (작업 루트 `recruit/`에서):
```bash
echo "--- 추적 파일 목록 ---"
git ls-files
echo "--- 워킹트리 클린? ---"
git status --short
echo "--- 코드 저장소가 제외됐는지 ---"
git check-ignore recruit_back recruit_front node_modules
```
Expected:
- `git ls-files` → `.gitignore`, `CLAUDE.md`, `api-contract.md`, `docs/superpowers/specs/2026-06-23-unified-harness-design.md`, `docs/superpowers/plans/2026-06-23-unified-harness.md` (계획 파일을 커밋했다면 포함)
- `git status --short` → 비어 있음(클린)
- `git check-ignore` → `recruit_back`, `recruit_front`, `node_modules` 모두 출력됨(제외 확인)

- [ ] **Step 2: 진입 문서 로딩 시나리오 점검 (수동 확인)**

확인 사항(읽고 체크):
- `recruit/CLAUDE.md` §2 읽기 순서가 백엔드 `CLAUDE.md`와 프론트 `AGENTS.md`를 정확히 가리키는가.
- §5 검증 명령의 프론트 스크립트명이 `package.json`과 일치하는가(`type-check`, `build`).
- §7이 백엔드 line 7을 "폐기"가 아니라 "명확화"로 다루는가(설계서 §9와 일치).

Expected: 세 항목 모두 일치. 불일치 시 Task 1로 돌아가 수정 후 재커밋.

- [ ] **Step 3: 완료 보고**

다음 형식으로 사용자에게 보고:
```text
통합 하네스 구축 완료
- recruit/CLAUDE.md (오케스트레이터)
- recruit/api-contract.md (계약 문서 골격)
- recruit/ 로컬 git 커밋: <해시 목록>

다음: 실제 화면 슬라이스 작업 시 §3 워크플로우대로 진행하고 api-contract.md를 채운다.
```

---

## Self-Review (작성자 체크 — 완료됨)

**1. Spec coverage:** 설계서 각 섹션 → 태스크 매핑
- §4 파일 레이아웃 → Task 1·2 (CLAUDE.md, api-contract.md 생성), `.gitignore`/spec은 선행 완료로 명시 ✓
- §5 화면 슬라이스 워크플로우 → CLAUDE.md §3 본문에 포함 ✓
- §6 api-contract 포맷 → Task 2 본문 ✓
- §7 검증 정책 → CLAUDE.md §5 ✓
- §8 git 구성 → 선행 완료 + CLAUDE.md §6 + Task 3 check-ignore 검증 ✓
- §9 충돌 정리 → CLAUDE.md §7 ✓
- §10 구현 산출물 → Task 1·2·3 ✓

**2. Placeholder scan:** `<수정패키지>`, `<백엔드 CLAUDE.md의 로컬 예시 키>`, api-contract 템플릿의 `<...>`는 **의도된 템플릿 변수**다(실제 슬라이스에서 채움). 미완성 스펙 섹션·TODO 없음 ✓

**3. Type/이름 일관성:** 프론트 auth 모듈명은 전 구간 `authApi.ts`로 통일(설계서의 `auth.ts` 오기 교정) ✓. 명령/경로는 사전 검증으로 실재 확인 ✓
