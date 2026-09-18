# AGENTS.md

## Project Overview

This repository is the Vue.js frontend for 신영증권's recruiting website (채용 Renewal). It is an operating, in-production application, not a project under initial setup.

It serves two apps from one codebase:

- Applicant-facing site at `/applicant` (job browsing, application forms, application status, FAQ/notice, profile).
- Admin console at `/admin` (job posting management, application review, stage results, interviews, statistics, role/menu management).

Treat this as an existing production codebase. Preserve existing UI structure, routing structure, component behavior, and business logic. Make focused, minimal changes for the requested task rather than broad rewrites.

## 읽기 순서와 디렉터리 구조

읽기 순서: 루트 `AGENTS.md` → `docs/domains/_index.md` → 대상 도메인 카드 → 이 문서(`recruit_front/AGENTS.md`).

주요 디렉터리(경로는 `recruit_front/src/` 기준):

- `views/admin/<도메인>/` — 관리자 화면. 도메인별 하위 폴더(`jobPosting/`, `stageResult/`, `interview/`, `applicationForm/`, `application/`, `faq/`, `dashboard/` 등)로 구성된다.
- `views/applicant/` — 지원자 화면(홈·공고·지원서·마이페이지 등). `application/` 하위에 지원서 작성 섹션 컴포넌트가 있다.
- `views/auth/` — 로그인·본인인증(NICE) 팝업 화면.
- `api/` — Axios 호출 모듈. 루트에 공통/화면별 API, `admin/`에 관리자 전용 API, `application/sections/`에 지원서 섹션별(학력·경력·자격증·어학·병역·수상·공백기간 등) API가 있다.
- `types/` — API 요청·응답 타입. `api/`와 같은 하위 구조(`admin/`, `application/`)를 따른다.
- `stores/` — Pinia 스토어(`authStore`, `menuStore`, `uiStore`).
- `routes/` — 라우트 정의. `adminRoutes.ts`·`applicantRoutes.ts`·`authRoutes.ts`를 모아 `index.ts`가 라우터를 만들고 인증/권한 가드를 붙인다.
- `layouts/` — 관리자/지원자 화면의 공통 레이아웃 뼈대.
- `common/` — 화면에 종속되지 않는 유틸(날짜, 파일 다운로드, 클라이언트 이벤트 로깅 등).
- `components/` — 여러 화면이 공유하는 컴포넌트.

공통 기반 파일:

- `api/client.ts` — Axios 인스턴스(`baseURL`=`VITE_API_BASE_URL`, `withCredentials: true`, 타임아웃 10초). 요청/응답 인터셉터로 `uiStore` 로딩 카운트를 관리하고, 401/403 응답 시 각각 `/login`·`/403`으로 리다이렉트한다(`skipAuthRedirect` 옵션으로 예외 처리 가능).
- `api/apiError.ts` — Axios 에러를 사용자용 한글 메시지로 변환하는 `getApiErrorMessage`.
- `routes/index.ts` — 라우터 생성과 `beforeEach` 가드(로그인 상태 복원, `meta.requiresAuth`, `meta.roles` 검사).
- `layouts/AdminLayout.vue` — 관리자 레이아웃(좌측 `AdminSidebar` + 우측 `RouterView`).
- `layouts/ApplicantLayout.vue` — 지원자 레이아웃(헤더/본문/푸터를 감싸는 고정폭 프레임).
- `stores/uiStore.ts` — 전역 로딩 카운트(`loadingCount`)만 관리하는 Pinia 스토어.
- `plugins/clientErrorHandlers.ts` — Vue `errorHandler`·`window` `error`·`unhandledrejection`을 잡아 스택을 정제한 뒤 클라이언트 이벤트 로그로 전송한다.

## Confirmed Tech Stack

Use the following stack as the project standard:

- Vue 3 (currently 3.5.x)
- Vite (currently 8.x)
- TypeScript (currently 6.x)
- Vue Router (currently 5.x)
- Pinia (currently 3.x)
- Axios (currently 1.x)
- ant-design-vue (currently 4.x)
- @ant-design/icons-vue
- CSS / SCSS / Less depending on existing files

Do not replace these technologies unless explicitly requested.

## TypeScript Rules

TypeScript is the confirmed project language.

For new code:

- Use `.ts` for TypeScript modules.
- Use `.vue` single-file components.
- Prefer `<script setup lang="ts">` for new Vue components.
- Define clear types for props, emits, API responses, route meta, store state, and reusable objects.
- Avoid `any` unless there is a practical reason.
- If `any` is used, keep its scope small.

For existing code:

- Do not blindly rewrite all existing JavaScript files.
- Convert JavaScript to TypeScript only when necessary for project setup, build stability, or explicit task requirements.
- Preserve existing behavior during migration.
- Fix TypeScript errors with minimal, accurate changes.
- Do not silence TypeScript errors by disabling strict checks unless explicitly requested.

## UI/UX Library Rules

The confirmed UI/UX component library is `ant-design-vue`.

Rules:

- Continue using `ant-design-vue`.
- Do not replace `ant-design-vue` with another UI library.
- Do not introduce Element Plus, Vuetify, Naive UI, Bootstrap Vue, or other UI libraries unless explicitly requested.
- Use `@ant-design/icons-vue` for Ant Design compatible icons.
- Preserve existing Ant Design Vue component patterns.
- Avoid global CSS changes that unintentionally break Ant Design Vue components.
- Use scoped styles where possible.
- Use `:deep()` only when overriding internal styles of third-party components is necessary.

## Package Manager Rules

Use npm (`package-lock.json` is the lockfile).

## Common Commands

```bash
npm run dev          # start local dev server
npm run build         # type-check + vite build (npm-run-all2, parallel)
npm run type-check    # vue-tsc --build
npm run test:unit     # vitest
npm run lint          # oxlint --fix, then eslint --fix --cache
npm run format        # prettier --write --experimental-cli src/
```

## Validation

Run `npm run type-check` by default before finishing a task. Run `npm run build` and/or `npm run test:unit` additionally when the change warrants a broader check.

## Dependency Rules

When build errors occur because dependencies are missing:

1. Check existing imports.
2. Check `package.json`.
3. Add only the necessary dependency.
4. Do not add unused libraries.
5. Do not upgrade major versions unless required.
6. Prefer versions compatible with the existing project.
7. Preserve Vue 3, Vite, TypeScript, Vue Router, Pinia, Axios, and ant-design-vue.

Do not replace existing libraries with alternatives.

Examples:

- Do not replace ant-design-vue with Element Plus.
- Do not replace Vue Router with another router.
- Do not replace Axios with Fetch unless explicitly requested.
- Do not replace Pinia with Vuex unless explicitly requested.
- Do not replace Vite with Vue CLI unless explicitly requested.

## Source Code Preservation Rules

Preserve the existing source as much as possible.

Do not rewrite large portions of code unless necessary to fix project setup, build, or runtime issues.

Avoid unnecessary refactoring.

Do not remove:

- Existing routes
- Existing components
- Existing stores
- Existing API modules
- Existing assets
- Existing styles
- Existing layout structure
- Existing Korean UI text
- Existing domain-specific naming

If deletion is necessary, explain why.

## Vue Coding Rules

Follow the existing code style where possible.

For new Vue components:

- Use Vue 3.
- Use TypeScript.
- Prefer `<script setup lang="ts">`.
- Use clear component names.
- Keep components focused.
- Avoid overly large components when separation is obvious.
- Preserve existing naming conventions.
- Keep template, script, and style sections consistent with existing files.

Do not forcibly rewrite existing Options API components unless the task requires it.

## Routing Rules

Before modifying routes:

1. Inspect the existing router configuration.
2. Check whether routes are lazy-loaded.
3. Check whether nested routes are used.
4. Check whether route meta fields are used.
5. Preserve the current route naming style.

Do not remove or rename routes unless explicitly requested.

If a route is broken because of a missing component path, fix the import/path instead of redesigning the router.

## Store Rules

The expected store library is Pinia.

Before modifying stores:

1. Inspect the existing store structure.
2. Preserve existing store names and responsibilities.
3. Do not create duplicate state for the same purpose.
4. Do not move business logic into components if it already belongs in a store.
5. Do not move UI-only state into global store unless necessary.

When adding or editing state, getters, or actions, keep naming consistent with the existing convention.

## API Rules

The expected HTTP client is Axios.

Before modifying API calls:

1. Inspect the existing Axios or API client configuration.
2. Preserve existing base URL handling.
3. Do not hard-code backend URLs.
4. Use environment variables for backend URLs where appropriate.
5. Do not silently change request/response shapes.
6. Do not invent backend API contracts without marking them clearly.

For Vite projects, prefer:

```env
VITE_API_BASE_URL=http://localhost:8080
```

If backend API details are unknown, keep the API integration mock-safe or leave a clear TODO comment.

## Environment Variable Rules

Do not commit real secrets.

Allowed:

```text
.env.example
```

Not allowed:

```text
.env
.env.local
.env.production
```

Environment variables should be documented in `.env.example`.

For Vite, client-side environment variables must use the `VITE_` prefix.

## Path Alias Rules

If the source uses imports such as:

```ts
import Component from '@/components/Component.vue'
```

then ensure the alias is configured properly.

For Vite, configure `vite.config.ts`:

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  }
})
```

Also configure `tsconfig.json` if needed:

```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

## Styling Rules

Follow the existing styling approach.

Possible styling methods:

- Scoped CSS
- SCSS
- Less
- CSS variables
- Global style files
- Component-level style blocks

Before changing global styles, check whether the change affects layout, header, navigation, modal, table, form, and common components.

Avoid unnecessary `!important`.

Use `!important` only when overriding third-party component styles cannot be handled more cleanly.

## Assets and Fonts

Do not remove existing assets.

Preserve:

- Logos
- Icons
- Images
- Fonts
- Static files
- Favicon
- Public files

If a build error occurs because an asset path is wrong, fix the path instead of removing the asset reference.

If custom fonts are used, preserve the existing font-family configuration.

## Korean Text Rules

This project may contain Korean UI text.

Preserve Korean labels, messages, menu names, button names, and domain terms unless explicitly requested.

Do not translate Korean UI text into English.

Examples of terms that should generally remain Korean:

- 로그인
- 회원가입
- 지원서
- 지원 현황
- 공고
- 채용
- 관리자
- 지원자
- 공지사항

## Build Error Resolution Priority

When resolving build errors, use this order:

1. Missing dependency
2. Wrong import path
3. Wrong alias configuration
4. Missing asset
5. Incorrect file extension
6. Case-sensitive path mismatch
7. Vue template syntax error
8. TypeScript syntax error
9. TypeScript type error
10. Library version mismatch
11. Vite configuration issue

Prefer root-cause fixes over temporary workarounds.

## Error Handling Rules

When fixing errors:

1. Identify the exact error.
2. Find the file and line causing it.
3. Fix the smallest necessary scope.
4. Re-run the build.
5. Repeat until the build passes or the remaining blocker is external.

Do not mask errors by removing functionality.

Do not comment out large blocks of code just to make the build pass.

## Runtime Error Rules

If the project builds but runtime errors remain:

1. Identify whether the issue is frontend-only or backend/API-related.
2. Check route/component loading.
3. Check API base URL.
4. Check missing environment variables.
5. Check Ant Design Vue registration.
6. Check Pinia initialization.
7. Check Axios interceptors.
8. Check browser console-equivalent logs if available.

If the issue requires a backend server that is not available, document the required backend endpoint clearly.

## Security Rules

Do not add secrets to the repository.

Do not expose:

- API tokens
- Passwords
- Private keys
- Internal server credentials
- Production database information
- Real user personal information

Use placeholders in examples.

## Git and Change Management Rules

Keep changes focused.

Avoid formatting unrelated files.

Avoid mass rewrites.

Do not modify generated files unless necessary.

Do not commit `node_modules`.

Do not commit build output such as:

```text
dist
coverage
.cache
.vite
```

## Documentation Rules

When code changes require a domain card update, follow root `AGENTS.md` §6 (문서 갱신 의무).

## Node Version

`package.json` declares `engines.node: "^20.19.0 || >=22.12.0"`. Use a Node version that satisfies this range.

Do not introduce dependency versions that require a newer Node version unless explicitly requested.

## Definition of Done

A task is complete when all applicable items are satisfied:

- `npm run type-check` passes.
- Existing routes are preserved.
- Existing stores are preserved.
- ant-design-vue is preserved and correctly registered.
- Existing Korean UI text is preserved.
- Environment variables are documented.
- No secrets are committed.
- No unnecessary rewrites are made.
- The relevant domain card is updated per root `AGENTS.md` §6, when applicable.
- Remaining limitations, if any, are clearly documented.

## Prohibited Actions

Do not:

- Recreate the entire project from scratch.
- Replace Vue with another framework.
- Replace Vite with Vue CLI unless explicitly requested.
- Replace TypeScript with JavaScript.
- Replace ant-design-vue with another UI library.
- Remove existing business logic to make the build pass.
- Hard-code backend URLs.
- Commit secrets.
- Commit `node_modules`.
- Commit build artifacts unless explicitly requested.
- Translate Korean UI text into English.
- Perform broad refactoring unrelated to the task.
- Change routing structure without checking existing usage.
- Add large new libraries without justification.

## Response Expectations

When completing a task, summarize:

1. What was changed.
2. Why it was changed.
3. What command was run.
4. Whether install/build/type-check passed.
5. Any remaining issues.
6. Files modified.

Prefer concise, technical summaries.
