# AGENTS.md

## Project Overview

This repository is a Vue.js frontend project.

The primary goal is to set up, normalize, and stabilize the project so that it can be installed, built, and executed reliably.

This project should be treated as an existing application source, not as a greenfield rewrite. Preserve the uploaded source code, UI structure, routing structure, component behavior, and business logic as much as possible.

## Current Project State

- The repository contains the project files except `node_modules`.
- The first major task is project setup and normalization.
- Use the existing uploaded files as the source of truth.
- Do not recreate the entire project from scratch unless the existing structure is unusable.
- Before making changes, inspect the current project structure, `package.json`, router files, store files, component files, configuration files, assets, and environment files.
- Prefer fixing and completing the existing project over replacing it.
- The initial success target is that dependencies can be installed and the project can pass the build command.

## Confirmed Tech Stack

Use the following stack as the project standard:

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Axios
- ant-design-vue
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

## Main Objectives

When working on this repository, prioritize the following:

1. Make the project installable.
2. Make the project buildable.
3. Make the project runnable in local development mode.
4. Preserve existing UI and business behavior.
5. Resolve missing imports, dependency issues, path alias issues, asset path issues, and build errors.
6. Normalize TypeScript, Vite, router, store, and Ant Design Vue configuration.
7. Improve structure only when it is clearly necessary.
8. Keep changes small, reviewable, and focused.

## Package Manager Rules

Detect the package manager from the repository:

- If `package-lock.json` exists, use `npm`.
- If `pnpm-lock.yaml` exists, use `pnpm`.
- If `yarn.lock` exists, use `yarn`.

Do not switch package managers without a strong reason.

If no lock file exists, prefer `npm` unless the repository clearly indicates another package manager.

Use the package manager that matches the existing lock file.

## Common Commands

Before running commands, inspect `package.json` and use the actual scripts defined there.

Typical commands may include:

```bash
npm install
npm run dev
npm run build
npm run preview
npm run lint
npm run type-check
```

If the package manager is not npm, use the equivalent command.

If a script does not exist, do not assume it exists. Either use the available equivalent script or update `package.json` only when necessary.

## Setup and Validation Flow

For the first setup task, follow this sequence:

1. Inspect project structure.
2. Inspect `package.json`.
3. Identify package manager.
4. Inspect Vite configuration.
5. Inspect TypeScript configuration.
6. Inspect router configuration.
7. Inspect Pinia/store configuration.
8. Inspect Ant Design Vue registration.
9. Inspect Axios/API configuration.
10. Install dependencies.
11. Run the build command.
12. Fix build errors one by one.
13. Re-run the build command.
14. Document remaining issues, if any.

Minimum validation:

```bash
npm run build
```

If TypeScript checking exists:

```bash
npm run type-check
```

If lint exists:

```bash
npm run lint
```

If tests exist:

```bash
npm run test
```

A task is not complete until the build succeeds or the remaining failure is clearly explained.

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

If you add or change project setup behavior, update documentation.

Important documentation files:

- `README.md`
- `.env.example`
- `AGENTS.md`

Document:

- install command
- dev command
- build command
- required Node version
- required environment variables
- known limitations

## Node Version

Use the Node version specified by the repository if available.

Check for:

- `.nvmrc`
- `.node-version`
- `package.json` engines field

If no version is specified, prefer a current LTS version compatible with Vue 3, Vite, TypeScript, and ant-design-vue.

Do not introduce dependency versions that require a newer Node version unless explicitly requested.

## Definition of Done

A task is complete when all applicable items are satisfied:

- Dependencies install successfully.
- The project builds successfully.
- TypeScript configuration is valid.
- Vite configuration is valid.
- No unresolved imports remain.
- No missing asset errors remain.
- Existing routes are preserved.
- Existing stores are preserved.
- ant-design-vue is preserved and correctly registered.
- Existing Korean UI text is preserved.
- Environment variables are documented.
- No secrets are committed.
- No unnecessary rewrites are made.
- Remaining limitations, if any, are clearly documented.

## Recommended First Task

For the first Codex task, the recommended instruction is:

> Set up and normalize this Vue 3 + Vite + TypeScript project. Install dependencies, inspect configuration, fix missing dependencies/imports/aliases/assets, ensure ant-design-vue is correctly configured, and make `npm run build` pass. Preserve existing source behavior and do not rewrite the application from scratch.

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
