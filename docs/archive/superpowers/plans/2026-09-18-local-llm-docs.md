# 로컬 LLM 유지보수용 문서 재편 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬 LLM 에이전트(컨텍스트 180K)가 `AGENTS.md` → `docs/domains/_index.md` → 도메인 카드 1~2개만 읽고 유지보수할 수 있게 문서를 재편한다.

**Architecture:** 도메인 카드 15개(API 계약 + 백엔드/프론트 파일 지도 + 규칙 + 변경 레시피)를 `docs/domains/`에 두고, `AGENTS.md`(루트·백엔드·프론트)를 정본 진입점으로 삼는다. 이력성 문서는 `docs/archive/`로 `git mv`하고, `tools/check-docs.mjs`가 경로 존재·소유 커버리지·크기를 검사한다.

**Tech Stack:** Markdown, Node(표준 모듈만, `node:test`), git

**설계서:** `docs/superpowers/specs/2026-09-18-local-llm-docs-design.md`

---

## 실행 전제 (반드시 지킬 것)

- **코드(`*.java`, `*.ts`, `*.vue`, 설정) 수정 금지.** 문서 작성 중 발견한 결함·오래된 주석은 최종 보고에만 적는다.
- **커밋은 사용자가 요청한 경우에만** 한다(`recruit/CLAUDE.md` §6). 각 체크포인트의 커밋 명령은 요청이 있을 때만 실행한다.
- 모든 명령은 레포 루트 `D:\recruit`(이하 `recruit/`) 기준이다. Bash(Git Bash) 문법으로 적는다.
- 운영 AES 키·LDAP·DB 접속정보를 문서에 쓰지 않는다. 예시 값만 쓴다.
- 문서 언어는 한국어. 클래스명·필드명·enum·API 경로·명령은 원문 그대로.
- 루트 `design/`(미추적, 사용자 진행 중 작업물)은 건드리지 않는다.

## 파일 구조

| 파일 | 작업 | 책임 |
|---|---|---|
| `tools/check-docs.mjs` | 생성 | 문서 점검 CLI + 테스트용 export 함수 |
| `tools/check-docs.test.mjs` | 생성 | `node:test` 단위 테스트 |
| `docs/domains/_index.md` | 생성 | 경로 표기, 카드 목록, 역색인, 공통 기반, 카드 없는 파일, 카드 템플릿 |
| `docs/domains/<card>.md` ×15 | 생성 | 도메인 카드 |
| `AGENTS.md` | 생성 | 루트 정본 진입점 |
| `CLAUDE.md` | 수정(축소) | `@AGENTS.md` + Claude 전용 규칙 |
| `recruit_back/recruit_backend/AGENTS.md` | 재작성 | 백엔드 규칙 (AGENTS+CLAUDE 병합) |
| `recruit_back/recruit_backend/CLAUDE.md` | 축소 | `@AGENTS.md` |
| `recruit_front/AGENTS.md` | 수정 | 프론트 규칙 정리 |
| `recruit_back/recruit_backend/docs/ops/` | 이동 | `docs/codex/ops/*.sql` |
| `.ignore` | 생성 | ripgrep 검색에서 `docs/archive/` 제외 |
| `docs/archive/**` | 이동 | 이력 문서 |

## 실행 순서와 병렬성

- Task 0 → 1 → 2 → 3 → 4 → 5 → 6은 순서대로 한다.
- **Task 7~21(카드 15개)은 서로 독립이다.** Task 6 이후 병렬로 진행해도 된다.
- Task 22(archive 이동)는 카드 전부 완료 후, Task 23 → 24 순서다.

---

## Task 0: 선행 조건 확인

**Files:** 없음 (확인만)

- [ ] **Step 1: 작업 트리 상태 확인**

Run: `git status --short`

허용되는 항목은 아래뿐이다.

```
?? design/
?? docs/superpowers/specs/2026-09-18-local-llm-docs-design.md
?? docs/superpowers/plans/2026-09-18-local-llm-docs.md
?? docs/archive/reports/local-llm-docs-restructure_design.html
```

- [ ] **Step 2: 판정**

위 목록 외의 변경(`M api-contract.md` 등)이 있으면 **중단하고 사용자에게 "진행 중 변경분을 먼저 커밋할지" 묻는다.** 사용자가 커밋을 지시하면 그 변경분만 커밋한 뒤 Step 1부터 다시 한다. 목록 외 변경이 없으면 Task 1로 간다.

- [ ] **Step 3: Node 버전 확인**

Run: `node --version`
Expected: `v20.19` 이상 또는 `v22.12` 이상 (프론트 `package.json` engines와 동일 기준)

---

## Task 1: 문서 점검 스크립트 (TDD)

**Files:**
- Create: `tools/check-docs.test.mjs`
- Create: `tools/check-docs.mjs`

- [ ] **Step 1: 실패하는 테스트 작성**

`tools/check-docs.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { checkDocs, expandToken, extractTokens, globToRegExp, sectionText } from './check-docs.mjs';

const BE = 'recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit';
const FE = 'recruit_front/src';

// overrides 값이 null이면 해당 파일을 만들지 않는다.
function makeRepo(overrides = {}) {
  const files = {
    'AGENTS.md': '# 루트\n- 색인: `docs/domains/_index.md`\n',
    'recruit_back/recruit_backend/AGENTS.md': '# 백엔드\n',
    'recruit_front/AGENTS.md': '# 프론트\n',
    [`${BE}/controller/FooController.java`]: 'class FooController {}\n',
    [`${FE}/views/admin/FooView.vue`]: '<template></template>\n',
    [`${FE}/views/error/NotFoundView.vue`]: '<template></template>\n',
    'docs/domains/_index.md': '# 색인\n\n## 카드 없는 파일\n- `{FE}/views/error/*`\n',
    'docs/domains/foo.md': [
      '# 푸 (`foo`)',
      '',
      '## 파일 지도',
      '- `{BE}/controller/FooController.java`',
      '- `{FE}/views/admin/FooView.vue`',
      '',
      '## API 계약',
      '',
    ].join('\n'),
    ...overrides,
  };
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'check-docs-'));
  for (const [rel, content] of Object.entries(files)) {
    if (content === null) continue;
    const abs = path.join(root, rel);
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    fs.writeFileSync(abs, content);
  }
  return root;
}

test('expandToken: 접두 표기를 실제 경로로 확장하고 줄번호·앵커를 제거한다', () => {
  assert.equal(expandToken('{BE}/service/A.java:12'), `${BE}/service/A.java`);
  assert.equal(expandToken('{FE}/views/X.vue:3-9'), `${FE}/views/X.vue`);
  assert.equal(expandToken('docs/adr/0001.md#결정'), 'docs/adr/0001.md');
  assert.equal(expandToken('recruit_front/package.json'), 'recruit_front/package.json');
  assert.equal(expandToken('ApiResponse'), null);
  assert.equal(expandToken('/admin/job-postings'), null);
});

test('globToRegExp: * 는 한 세그먼트 안, **/ 는 0개 이상 디렉터리와 매칭한다', () => {
  assert.ok(globToRegExp('a/*.java').test('a/B.java'));
  assert.ok(!globToRegExp('a/*.java').test('a/b/C.java'));
  assert.ok(globToRegExp('a/**/*.vue').test('a/X.vue'));
  assert.ok(globToRegExp('a/**/*.vue').test('a/b/c/X.vue'));
  assert.ok(!globToRegExp('a/**/*.vue').test('b/X.vue'));
});

test('extractTokens: 펜스 코드 블록 안의 토큰은 무시하고 줄번호를 기록한다', () => {
  const text = '`a`\n```\n`b`\n```\n`c` `d e`';
  assert.deepEqual(extractTokens(text), [
    { token: 'a', line: 1 },
    { token: 'c', line: 5 },
  ]);
});

test('sectionText: 제목 다음부터 다음 ## 제목 전까지만 돌려준다', () => {
  const text = '# t\n## 파일 지도\n### 백엔드\n`x`\n## API 계약\n`y`';
  assert.equal(sectionText(text, '## 파일 지도'), '### 백엔드\n`x`');
  assert.equal(sectionText(text, '## 없음'), '');
});

test('정상 저장소는 오류와 경고가 없다', () => {
  const { errors, warnings } = checkDocs(makeRepo());
  assert.deepEqual(errors, []);
  assert.deepEqual(warnings, []);
});

test('존재하지 않는 경로는 파일:줄과 함께 오류로 보고한다', () => {
  const root = makeRepo({ 'docs/domains/bar.md': '# 바\n\n규칙: `{BE}/service/MissingService.java`\n' });
  const { errors } = checkDocs(root);
  assert.deepEqual(errors, ['docs/domains/bar.md:3: 경로 없음 `{BE}/service/MissingService.java`']);
});

test('매칭이 0개인 글롭 경로는 오류로 보고한다', () => {
  const root = makeRepo({ 'docs/domains/bar.md': '# 바\n`{FE}/views/nothing/*`\n' });
  const { errors } = checkDocs(root);
  assert.deepEqual(errors, ['docs/domains/bar.md:2: 경로 없음 `{FE}/views/nothing/*`']);
});

test('어느 카드에도 없는 컨트롤러는 소유 누락 오류다', () => {
  const root = makeRepo({ [`${BE}/controller/BarController.java`]: 'class BarController {}\n' });
  const { errors } = checkDocs(root);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /BarController\.java:1: 소유 카드 없음/);
});

test('두 카드가 같은 화면을 소유하면 중복 오류다', () => {
  const root = makeRepo({ 'docs/domains/bar.md': '# 바\n\n## 파일 지도\n- `{FE}/views/admin/*`\n' });
  const { errors } = checkDocs(root);
  assert.deepEqual(errors, [
    `${FE}/views/admin/FooView.vue:1: 소유 카드 중복 — docs/domains/bar.md, docs/domains/foo.md`,
  ]);
});

test('파일 지도 절 밖에서 언급한 파일은 소유로 치지 않는다', () => {
  const root = makeRepo({
    'docs/domains/bar.md': '# 바\n\n## 규칙·불변식\n- `{FE}/views/admin/FooView.vue` 참고\n',
  });
  const { errors } = checkDocs(root);
  assert.deepEqual(errors, []);
});

test('크기: 권장 초과는 경고, 상한 초과는 오류다', () => {
  const root = makeRepo({
    'AGENTS.md': '#'.repeat(13 * 1024),
    'docs/domains/big.md': '#'.repeat(41 * 1024),
  });
  const { errors, warnings } = checkDocs(root);
  assert.deepEqual(warnings, ['AGENTS.md:1: 경고 크기 13.0KB — 권장 12.0KB 초과']);
  assert.deepEqual(errors, ['docs/domains/big.md:1: 크기 41.0KB — 상한 40.0KB 초과']);
});

test('필수 문서가 없으면 오류다', () => {
  const root = makeRepo({ 'docs/domains/_index.md': null, 'recruit_front/AGENTS.md': null });
  const { errors } = checkDocs(root);
  assert.ok(errors.includes('recruit_front/AGENTS.md:1: 파일 없음'));
  assert.ok(errors.includes('docs/domains/_index.md:1: 파일 없음'));
});
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `node --test tools/check-docs.test.mjs`
Expected: FAIL — `ERR_MODULE_NOT_FOUND` (`check-docs.mjs` 없음)

- [ ] **Step 3: 구현 작성**

`tools/check-docs.mjs`:

```js
#!/usr/bin/env node
// 문서 점검: (1) 백틱 경로 존재 (2) Controller·화면 소유 커버리지 (3) 문서 크기
// 사용법: node tools/check-docs.mjs [레포 루트]   (기본값: 이 파일의 상위 디렉터리)
// 규약 설명: AGENTS.md 6절, docs/domains/_index.md
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const PREFIXES = {
  '{BE}': 'recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit',
  '{BT}': 'recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit',
  '{BR}': 'recruit_back/recruit_backend/src/main/resources',
  '{FE}': 'recruit_front/src',
};
const PATH_STARTS = ['recruit_back/', 'recruit_front/', 'docs/'];
const SKIP_DIRS = new Set(['node_modules', '.git', 'build', 'dist', '.gradle']);
const KB = 1024;
const REQUIRED_DOCS = [
  { file: 'AGENTS.md', warn: 12 * KB, max: 16 * KB },
  { file: 'recruit_back/recruit_backend/AGENTS.md', warn: 15 * KB, max: 20 * KB },
  { file: 'recruit_front/AGENTS.md', warn: 15 * KB, max: 20 * KB },
  { file: 'docs/domains/_index.md', warn: 8 * KB, max: 12 * KB },
];
const DOMAINS_DIR = 'docs/domains';
const INDEX_FILE = 'docs/domains/_index.md';
const CARD_LIMIT = { warn: 30 * KB, max: 40 * KB };
const OWNED_TARGETS = [
  `${PREFIXES['{BE}']}/controller/*Controller.java`,
  `${PREFIXES['{FE}']}/views/**/*.vue`,
];
const FILE_MAP_HEADING = '## 파일 지도';
const NO_CARD_HEADING = '## 카드 없는 파일';

export function expandToken(token) {
  const t = token.replace(/#.*$/, '').replace(/:\d+(-\d+)?$/, '');
  for (const [prefix, real] of Object.entries(PREFIXES)) {
    if (t.startsWith(prefix)) return real + t.slice(prefix.length);
  }
  return PATH_STARTS.some((s) => t.startsWith(s)) ? t : null;
}

export function extractTokens(text) {
  const tokens = [];
  let inFence = false;
  text.split(/\r?\n/).forEach((line, i) => {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      return;
    }
    if (inFence) return;
    for (const m of line.matchAll(/`([^`\s]+)`/g)) tokens.push({ token: m[1], line: i + 1 });
  });
  return tokens;
}

export function sectionText(text, heading) {
  const lines = text.split(/\r?\n/);
  const start = lines.findIndex((l) => l.trim() === heading);
  if (start < 0) return '';
  let end = lines.length;
  let inFence = false;
  for (let i = start + 1; i < lines.length; i++) {
    if (/^\s*(```|~~~)/.test(lines[i])) {
      inFence = !inFence;
    } else if (!inFence && lines[i].startsWith('## ')) {
      end = i;
      break;
    }
  }
  return lines.slice(start + 1, end).join('\n');
}

export function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*') {
      if (glob[i + 1] === '*') {
        i++;
        if (glob[i + 1] === '/') {
          i++;
          re += '(?:.*/)?';
        } else {
          re += '.*';
        }
      } else {
        re += '[^/]*';
      }
    } else if ('\\^$.|?+()[]{}'.includes(c)) {
      re += `\\${c}`;
    } else {
      re += c;
    }
  }
  return new RegExp(`^${re}$`);
}

function listFiles(root, baseRel) {
  const files = [];
  const walk = (rel) => {
    let entries;
    try {
      entries = fs.readdirSync(path.join(root, rel), { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      const child = rel ? `${rel}/${e.name}` : e.name;
      if (e.isDirectory()) {
        if (!SKIP_DIRS.has(e.name)) walk(child);
      } else {
        files.push(child);
      }
    }
  };
  walk(baseRel);
  return files.sort();
}

export function globFiles(root, glob) {
  const head = glob.slice(0, glob.indexOf('*'));
  const base = head.includes('/') ? head.slice(0, head.lastIndexOf('/')) : '';
  const re = globToRegExp(glob);
  return listFiles(root, base).filter((f) => re.test(f));
}

function matches(file, pattern) {
  return pattern.includes('*') ? globToRegExp(pattern).test(file) : file === pattern.replace(/\/$/, '');
}

const kb = (bytes) => (bytes / KB).toFixed(1);

export function checkDocs(root) {
  const errors = [];
  const warnings = [];
  const exists = (rel) => fs.existsSync(path.join(root, rel));
  const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');

  const cards = exists(DOMAINS_DIR)
    ? fs
        .readdirSync(path.join(root, DOMAINS_DIR))
        .filter((f) => f.endsWith('.md') && f !== '_index.md')
        .sort()
        .map((f) => ({ file: `${DOMAINS_DIR}/${f}`, ...CARD_LIMIT }))
    : [];

  for (const doc of [...REQUIRED_DOCS, ...cards]) {
    if (!exists(doc.file)) {
      errors.push(`${doc.file}:1: 파일 없음`);
      continue;
    }
    const text = read(doc.file);
    const size = Buffer.byteLength(text, 'utf8');
    if (size > doc.max) errors.push(`${doc.file}:1: 크기 ${kb(size)}KB — 상한 ${kb(doc.max)}KB 초과`);
    else if (size > doc.warn) warnings.push(`${doc.file}:1: 경고 크기 ${kb(size)}KB — 권장 ${kb(doc.warn)}KB 초과`);
    for (const { token, line } of extractTokens(text)) {
      const rel = expandToken(token);
      if (rel === null) continue;
      const found = rel.includes('*') ? globFiles(root, rel).length > 0 : exists(rel);
      if (!found) errors.push(`${doc.file}:${line}: 경로 없음 \`${token}\``);
    }
  }

  const patternsIn = (file, heading) =>
    exists(file)
      ? extractTokens(sectionText(read(file), heading))
          .map((t) => expandToken(t.token))
          .filter((p) => p !== null)
      : [];
  const owners = cards.map((c) => ({ file: c.file, patterns: patternsIn(c.file, FILE_MAP_HEADING) }));
  const exempt = patternsIn(INDEX_FILE, NO_CARD_HEADING);

  for (const target of OWNED_TARGETS.flatMap((g) => globFiles(root, g))) {
    if (exempt.some((p) => matches(target, p))) continue;
    const owning = owners.filter((o) => o.patterns.some((p) => matches(target, p))).map((o) => o.file);
    if (owning.length === 0) {
      errors.push(`${target}:1: 소유 카드 없음 — 카드의 "${FILE_MAP_HEADING}" 또는 _index.md "${NO_CARD_HEADING}"에 추가`);
    } else if (owning.length > 1) {
      errors.push(`${target}:1: 소유 카드 중복 — ${owning.join(', ')}`);
    }
  }
  return { errors, warnings };
}

function main() {
  const defaultRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const root = path.resolve(process.argv[2] ?? defaultRoot);
  const { errors, warnings } = checkDocs(root);
  for (const line of [...warnings, ...errors]) console.log(line);
  if (errors.length > 0) {
    console.log(`문서 점검 실패: 오류 ${errors.length}건, 경고 ${warnings.length}건`);
    process.exit(1);
  }
  console.log(`문서 점검 통과: 경고 ${warnings.length}건`);
}

// Windows 드라이브 문자 대소문자 차이를 피하려고 소문자로 비교한다.
const invokedPath = process.argv[1] ? path.resolve(process.argv[1]).toLowerCase() : '';
if (invokedPath === fileURLToPath(import.meta.url).toLowerCase()) main();
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `node --test tools/check-docs.test.mjs`
Expected: `pass 12`, `fail 0` (Node 버전에 따라 `ℹ pass 12` 또는 `# pass 12` 형식)

- [ ] **Step 5: 현재 저장소에서 실행 (실패가 정상)**

Run: `node tools/check-docs.mjs; echo "exit=$?"`
Expected: `파일 없음` 오류(AGENTS.md 등)와 `소유 카드 없음` 오류가 출력되고 `exit=1`. 스크립트가 예외 없이 끝까지 도는지만 확인한다.

- [ ] **Step 6: 커밋 체크포인트 (요청 시에만)**

```bash
git add tools/check-docs.mjs tools/check-docs.test.mjs
git commit -m "chore(docs): 문서 점검 스크립트 추가"
```

---

## Task 2: 운영 DDL 이동

카드가 새 경로를 참조하므로 카드 작성 전에 옮긴다.

**Files:**
- Move: `recruit_back/recruit_backend/docs/codex/ops/` → `recruit_back/recruit_backend/docs/ops/`

- [ ] **Step 1: 이동**

```bash
git mv recruit_back/recruit_backend/docs/codex/ops recruit_back/recruit_backend/docs/ops
```

- [ ] **Step 2: 확인**

Run: `ls recruit_back/recruit_backend/docs/ops`
Expected: `.sql` 8개 (`fix-employee-dept-name-unique-drop.sql`, `phase-07d-stage-result-version-column.sql`, `phase-09a-activity-log-ddl.sql`, `phase-09c-retention-ddl.sql`, `phase-09d-1-purge-execute-ddl.sql`, `phase-09d-2-attachment-saga-ddl.sql`, `phase-09e-reconciliation-ddl.sql`, `role-mapping-user-role-mapping-ddl.sql`)

- [ ] **Step 3: 오래된 참조 기록 (수정 금지)**

Run: `grep -rn "docs/codex/ops" --include=*.java --include=*.md recruit_back/recruit_backend/src recruit_back/recruit_backend/docs/adr`
출력된 위치를 최종 보고용 "오래된 참조" 목록에 적는다. 알려진 항목: `{BE}/domain/entity/StageResult.java:77`.

---

## Task 3: 도메인 색인 `_index.md`

**Files:**
- Create: `docs/domains/_index.md`

- [ ] **Step 1: 파일 작성**

`docs/domains/_index.md` 전체 내용:

````markdown
# 도메인 카드 색인

작업할 기능을 아래 역색인에서 찾아 **해당 카드만** 읽는다. 카드 하나에 그 도메인의 API 계약, 백엔드·프론트 파일 지도, 규칙, 변경 레시피가 모두 있다.

## 경로 표기

| 표기 | 실제 경로 (레포 루트 기준) |
|---|---|
| `{BE}` | `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit` |
| `{BT}` | `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit` |
| `{BR}` | `recruit_back/recruit_backend/src/main/resources` |
| `{FE}` | `recruit_front/src` |

- 백엔드 API에는 모두 `/api` 접두가 붙는다(`{BE}/config/WebMvcConfig.java`). 카드의 API 경로는 `/api`를 뺀 형태다.
- 문서 안의 파일 경로는 **레포 루트 기준**으로 쓴다(레포별 AGENTS.md 포함). 예: 백엔드 ADR은 `recruit_back/recruit_backend/docs/adr/`로 쓴다(백엔드 디렉터리 기준 상대 경로 금지).
- 카드 `## 파일 지도` 절에 적힌 경로가 그 파일의 **소유 카드**다. `*Controller.java`와 `views/**/*.vue`는 정확히 한 카드가 소유해야 한다(`node tools/check-docs.mjs`가 검사).

## 카드 목록

| 카드 | 도메인 | 주 사용자 |
|---|---|---|
| [auth-account](auth-account.md) | 로그인(세션·LDAP)·본인인증·지원자 가입/계정 | 지원자·관리자 |
| [role-menu](role-menu.md) | 부서 권한 매핑·메뉴 | 관리자 |
| [job-posting](job-posting.md) | 공고·직무·근무지·공고 이미지·첨부 요건 | 관리자·지원자 |
| [question](question.md) | 질문 템플릿·공고별 질문 | 관리자 |
| [application-form](application-form.md) | 공고별 지원서 양식 설정(config·layout) | 관리자 |
| [application](application.md) | 지원서 작성·제출·내 지원 현황 | 지원자 |
| [attachment](attachment.md) | 첨부파일 저장·다운로드·삭제·저장소 점검 | 지원자·관리자 |
| [admin-application](admin-application.md) | 지원현황 검색·지원서 상세·엑셀·PDF | 관리자 |
| [stage-result](stage-result.md) | 전형·전형결과(업로드·정정·지원자 조회) | 관리자·지원자 |
| [interview](interview.md) | 면접 일정·면접관·면접 평가 | 관리자·면접관·지원자 |
| [master-data](master-data.md) | 공통코드·학교·주소 검색 | 관리자·지원자 |
| [board](board.md) | FAQ·공지사항 | 관리자·지원자 |
| [statistics](statistics.md) | 관리자 대시보드·통계 | 관리자 |
| [privacy-audit](privacy-audit.md) | 개인정보 보존·파기·감사 로그 | 관리자 |
| [client-event-log](client-event-log.md) | 프론트 이벤트·오류 로그 수집 | 시스템·관리자 |

## 역색인

### 라우트 → 카드

| 라우트 name | 경로 | 카드 |
|---|---|---|
| `Login` · `NiceAuthPopup` | `/login` · `/nice-auth` | auth-account |
| `Signup` · `accountRecovery` · `ApplicantProfile` | `/applicant/signup` · `/applicant/accountRecovery` · `/applicant/profile` | auth-account |
| `AdminMenuManage` · `AdminRoleMapping` | `/admin/menus` · `/admin/role-mappings` | role-menu |
| `AdminJobPostingList` · `AdminJobPostingCreate` · `AdminJobPostingDetail` · `AdminJobPostingEdit` | `/admin/job-postings…` | job-posting |
| `ApplicantRecruits` · `ApplicationDetail` | `/applicant/recruits` · `/applicant/:jobPostingId/detail` | job-posting |
| `AdminQuestionTemplates` · `AdminJobPostingQuestionTemplateEdit` | `/admin/question-templates` · `/admin/question-template/:id?/edit` | question |
| `AdminApplicationFormList` · `AdminApplicationFormDetail` | `/admin/application-forms…` | application-form |
| `ApplicationStart` · `application` | `/applicant/:jobPostingId/apply` · `/applicant/:applicationId/form` | application |
| `AdminApplicationStatus` · `AdminApplication` | `/admin/applications` · `/admin/applications/:applicationId` | admin-application |
| `AdminStageResult` | `/admin/stage-results` | stage-result |
| `InterviewSchedulingSetting` | `/admin/interview` | interview |
| `AdminCommonCodeManage` | `/admin/codes` | master-data |
| `AdminFaqManage` · `ApplicantFaq` · `NoticeList` | `/admin/faqs` · `/applicant/faq` · `/applicant/noticeList` | board |
| `AdminHome` | `/admin` | statistics |
| `ApplicantHome` · `ApplicantBenefits` · `ApplicantDutyIntroduction` · `ApplicantRecruitProcedure` · `ApplicantPrivacy` | `/applicant/…` | 카드 없음(정적 화면) |

### API 경로 접두 → 카드 (`/api` 생략)

| 접두 | 카드 |
|---|---|
| `/auth` · `/applicant/account` | auth-account |
| `/menu` · `/admin/role-mappings` | role-menu |
| `/admin/job-postings`(기본·`/images`·`/attachment-requirements`) · `/job-postings` | job-posting |
| `/admin/question-templates` · `/admin/job-postings/{id}/questions` | question |
| `/admin/application-forms` · `/admin/job-postings/{id}/application-form-config` · `/admin/job-postings/{id}/application-form-layout` | application-form |
| `/applications`(기본·섹션·`/answers`·`/questions`) | application |
| `/applications/{id}/attachments` · `/admin/applications/{id}/attachments/{attachmentId}/…` · `/admin/attachments/storage-health` | attachment |
| `/admin/applications`(검색·상세·섹션 조회·`/export`·`/pdf`) · `/admin/job-postings/{id}/applications` | admin-application |
| `/admin/job-postings/{id}/stages` · `/admin/stages/{id}/results` · `/applications/{id}/stage-results` | stage-result |
| `/admin/job-postings/{id}/interviews` · `/admin/interviews` · `/admin/job-postings/{id}/interview-schedules` · `/admin/applications/{id}/interview-evaluations` · `/interviewer` · `/applicant/interviews` · `/applicant/applications/{id}/interviews` | interview |
| `/codes` · `/admin/codes` · `/schools` · `/admin/schools` · `/addresses` | master-data |
| `/faqs` · `/admin/faq-categories` · `/board/notices` | board |
| `/admin/job-postings/{id}/statistics` | statistics |
| `/admin/retention` · `/admin/audit` | privacy-audit |
| `/client-events` · `/admin/client-events` | client-event-log |

### 키워드 → 카드

| 키워드 | 카드 |
|---|---|
| 로그인, 세션, LDAP, 본인인증(NICE), 회원가입, 비밀번호, 휴대폰 변경, 계정 찾기 | auth-account |
| 권한, 역할, 부서 권한 매핑, 메뉴, 사이드바, 헤더 메뉴 | role-menu |
| 공고, 채용공고, 모집분야, 직무(`JobPosition`), 근무지, 공고 이미지, 첨부 요건, 신입/경력, 마감 | job-posting |
| 질문 템플릿, 질문 은행, 공고 질문 | question |
| 지원서 양식, 지원서 설정, 폼 레이아웃, 필수/선택 항목, 수정 가능 기간 | application-form |
| 지원서 작성·제출, 기본정보, 보훈, 학력, 학기별 성적, 경력, 자격증, 어학, 병역, 수상, 공백기간, 자기소개·답변, 완성도, 내 지원 현황 | application |
| 첨부파일, 파일 업로드/다운로드, 저장소 점검 | attachment |
| 지원현황, 지원서 검색, 관리자 지원서 상세, 엑셀 다운로드, 컬럼 선택, PDF | admin-application |
| 전형, 전형결과, 합격/불합격, 결과 업로드, 결과 정정, 지원자 결과 조회 | stage-result |
| 면접, 면접 일정, 스케줄링, 면접관, 면접 평가 | interview |
| 공통코드, 학교, 학교 검색, 학교 가져오기, 주소 검색 | master-data |
| FAQ, 공지사항, 게시판 | board |
| 대시보드, 통계, 퍼널, 일별 추이 | statistics |
| 개인정보 보존, 파기(purge), 보존 정책(retention), 보류(hold), 감사 로그, activity log | privacy-audit |
| 클라이언트 이벤트, 텔레메트리, 프론트 오류 로그 | client-event-log |

## 공통 기반 (카드 없음 — 레포 AGENTS.md 참조)

- 백엔드: `{BE}/dto/response/ApiResponse.java`, `{BE}/exception/GlobalExceptionHandler.java`, `{BE}/domain/entity/BaseEntity.java`, `{BE}/common/`, `{BE}/config/WebMvcConfig.java`, `{BE}/config/CorrelationIdFilter.java`
- 프론트: `{FE}/api/client.ts`, `{FE}/api/apiError.ts`, `{FE}/layouts/`, `{FE}/routes/index.ts`, `{FE}/stores/uiStore.ts`, `{FE}/common/`, `{FE}/styles/`

## 카드 없는 파일

소유 카드가 없는 화면(주로 지원자 정적 페이지)이다. 새로 추가하면 여기에 적거나 카드에 등록한다.

- `{FE}/views/applicant/ApplicantHomeView.vue` — 지원자 홈
- `{FE}/views/applicant/ApplicantQuickLinkCards.vue` — 홈 바로가기 카드
- `{FE}/views/applicant/banners/*` — 홈 배너
- `{FE}/views/applicant/ApplicantBenefits.vue` — 인사제도(보상·교육·복리후생 탭)
- `{FE}/views/applicant/ApplicantInfoTabPanel.vue` — 인사제도 탭 패널
- `{FE}/views/applicant/ApplicantDutyIntroduction.vue` — 직무소개
- `{FE}/views/applicant/DutyIntroModalBody.vue` — 직무소개 모달
- `{FE}/views/applicant/ApplicantRecruitProcedure.vue` — 채용절차
- `{FE}/views/applicant/ApplicantPrivacy.vue` — 개인정보처리방침
- `{FE}/views/applicant/ApplicantBreadcrumb.vue` — 지원자 공통 breadcrumb
- `{FE}/views/common/htmlView.vue` — HTML 본문 렌더 공용 컴포넌트
- `{FE}/views/error/*` — 403·404
- `{FE}/views/samples/*` — 개발용 샘플

## 카드 템플릿

새 카드는 아래 형식을 따른다. `## 파일 지도` 제목은 점검 스크립트가 쓰므로 바꾸지 않는다.

```markdown
# <도메인 한글명> (`<card-name>`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [<카드>](<카드>.md)

## 요약
## 용어
## 파일 지도
### 백엔드
### 프론트
## API 계약
### 엔드포인트 상세
## 규칙·불변식
## 변경 레시피
## 검증
## 함정·결정
```
````

- [ ] **Step 2: 점검**

Run: `node tools/check-docs.mjs | grep "_index.md"`
Expected: `_index.md`에 대한 `경로 없음` 오류 0건. 크기 경고(8KB 초과)는 허용한다. 12KB 상한 오류가 나오면 키워드 표를 줄인다.

---

## Task 4: 루트 `AGENTS.md` 작성과 루트 `CLAUDE.md` 축소

현재 루트 `CLAUDE.md`의 §3 워크플로우·§4 계약 규약·§5 검증·§6 git 규칙과 홈 `CLAUDE.md`의 언어·엔지니어링 규칙을 계승한다. 계약 기준만 `api-contract.md`에서 카드로 바꾼다.

**Files:**
- Create: `AGENTS.md`
- Modify: `CLAUDE.md` (전체 교체)

- [ ] **Step 1: `AGENTS.md` 작성**

`AGENTS.md` 전체 내용:

````markdown
# AGENTS.md — 신영증권 채용 Renewal

모든 작업의 진입점이다. 읽는 순서: 이 문서 → `docs/domains/_index.md`(대상 카드 찾기) → 도메인 카드 → 수정할 레포의 AGENTS.md.
응답·보고·문서는 한국어로 쓴다. 클래스명·필드명·enum·API 경로·명령은 원문 그대로 쓴다.

## 1. 구성

| 영역 | 경로 | 내용 |
|---|---|---|
| 백엔드 | `recruit_back/recruit_backend/` | Spring Boot 4 · Java 17 · JPA · Spring Security(세션·LDAP) · Gradle. 규칙: `recruit_back/recruit_backend/AGENTS.md` |
| 프론트 | `recruit_front/` | Vue 3 · Vite · TypeScript · Pinia · ant-design-vue · Axios. 규칙: `recruit_front/AGENTS.md` |
| 도메인 카드 | `docs/domains/` | 도메인별 API 계약·파일 지도·규칙·변경 레시피. 색인: `docs/domains/_index.md` |
| ADR | `recruit_back/recruit_backend/docs/adr/` | 아키텍처 결정 기록 |
| 수동 DDL | `recruit_back/recruit_backend/docs/ops/` | 운영 DB에 수동 반영하는 SQL |
| 과거 이력 | `docs/archive/` | 옛 설계·구현 이력·보고서. **작업 근거로 쓰지 않는다** |
| 문서 점검 | `tools/check-docs.mjs` | 카드 경로·소유·크기 검사 |

- 백엔드 API에는 모두 `/api` 접두가 붙는다. 카드의 API 경로는 `/api`를 뺀 형태다.
- 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}`의 정의는 `docs/domains/_index.md`에 있다.

## 2. 작업 원칙

- 가정을 먼저 밝힌다. 모호하면 추측하지 말고 묻는다.
- 요청을 해결하는 최소 코드만 쓴다. 추측성 기능·추상화·설정·의존성을 넣지 않는다.
- 요청 범위의 파일·줄만 고친다. 인접 코드 리팩터링과 무관한 포맷 변경을 하지 않는다. 기존 스타일을 따른다.
- 무관한 결함·죽은 코드를 발견하면 고치지 말고 보고한다.
- 성공 기준을 정하고, 가능하면 테스트로 재현한 뒤 고친다. 무엇을 검증했고 무엇을 못 했는지 보고한다.
- 코드와 문서가 다르면 코드가 기준이다. 문서를 고치고 보고한다.

## 3. 화면 슬라이스 워크플로우

작업 단위는 하나의 화면(기능) 슬라이스다. 지정된 화면·API만 다루고 범위를 넓히지 않는다.

0. 범위 고정: `_index.md`로 카드를 찾고 카드의 API 계약·규칙을 읽는다.
1. 계약 정렬: 바뀔 엔드포인트를 카드 `## API 계약` 표에 🟡로 먼저 적는다.
2. 백엔드 구현: 백엔드 AGENTS.md 규칙을 따른다.
3. 백엔드 검증: 수정한 클래스·패키지 테스트만 실행한다(5절).
4. 프론트 구현: `src/api` 모듈 → 타입 → 화면·store·route 순서. 프론트 AGENTS.md 규칙을 따른다.
5. 프론트 검증: `npm run type-check`.
6. 계약 확정: 표를 구현과 일치시키고 🟢로 바꾼다.
7. 카드 갱신과 문서 점검(6절).
8. 보고: 변경 파일, 테스트 결과, 계약 변경, 남은 이슈.

기본 방향은 백엔드 → 프론트다. API를 바꾸지 않는 한쪽 작업은 해당 단계를 생략하되 "계약 영향 없음"을 확인해 보고한다.

## 4. API 계약 규약

- 계약의 단일 기준은 각 카드의 `## API 계약` 절이다. 옛 `api-contract.md`는 `docs/archive/`로 옮겨졌다.
- 상태: 🟢 확정(front-back 구현·검증 완료) / 🟡 초안(구현 중) / 🔴 불명확(사용자 확인 필요) / ⛔ 폐지.
- 요청·응답은 필드 모양 요약만 적는다. 정확한 타입·검증은 백엔드 DTO가 단일 출처다.
- 계약을 발명하지 않는다. 불명확하면 🔴로 적고 사용자에게 확인한다.
- 여러 화면이 쓰는 엔드포인트는 컨트롤러를 소유한 카드 한 곳에만 정의하고, 다른 카드는 링크한다.
- 변경 이력은 git log로 대신한다.

## 5. 검증

전체 리그레션·전체 빌드는 명시적으로 요청받았을 때만 한다. 평소에는 변경 범위만 검증한다.

백엔드(`recruit_back/recruit_backend/`에서). AES 키는 백엔드 AGENTS.md의 로컬 예시 값만 쓴다.

```bash
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.StageResult*" --no-daemon
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.service.StageResult*" --no-daemon
```

프론트(`recruit_front/`에서): `npm run type-check`(기본), `npm run build`(필요 시), `npm run test:unit`(필요 시).

문서(레포 루트에서): `node tools/check-docs.mjs` — 오류 0건이어야 한다.

## 6. 문서 갱신 의무 (완료 조건)

코드를 바꾼 변경에 카드 갱신이 빠지면 미완료다.

- 파일 추가·삭제·이름 변경 → 카드 `## 파일 지도`.
- 엔드포인트·요청·응답 변경 → 카드 `## API 계약`.
- 비즈니스 규칙·상태 전이·권한 변경 → 카드 `## 규칙·불변식`.
- 새 `*Controller.java`나 `views/**/*.vue`는 정확히 한 카드의 `## 파일 지도`에 등록한다. 카드가 없는 정적 화면은 `_index.md`의 "카드 없는 파일"에 적는다. 점검 스크립트가 누락·중복을 잡는다.
- `## 파일 지도`에는 그 카드가 소유한 파일만 적는다. 다른 카드의 파일은 다른 절에서 언급하고 그 카드로 링크한다.
- 새 도메인이면 `_index.md`의 카드 템플릿으로 카드를 만들고 색인에 추가한다.
- 카드는 30KB 권장, 40KB 상한이다. 넘으면 하위 도메인으로 나누고 색인을 고친다.
- 완료 전에 `node tools/check-docs.mjs`를 통과시킨다.

## 7. git 규칙

- `recruit/`는 백엔드·프론트·문서를 함께 추적하는 단일 모노레포다.
- 명확한 요청 없이 `git commit`·`git push`·브랜치 조작을 하지 않는다.
- 커밋 메시지는 `feat(<카드명>): ...`, `fix(<카드명>): ...` 형식이다. 스코프는 도메인 카드 이름을 쓴다.
- 파일 이동은 `git mv`로 한다.

## 8. 금지 사항

- 운영 AES 키·LDAP·DB 접속정보를 사용하거나 문서·코드·커밋에 쓰지 않는다. 예시 값만 쓴다.
- 백엔드 디렉터리 안에 프론트 코드·정적 리소스를 만들지 않는다.
- `docs/archive/` 문서를 현행 규칙의 근거로 쓰지 않는다. 과거 경위를 조사할 때만 참고한다.
- 요청 없이 대규모 리팩터링, 패키지 구조 변경, 새 의존성 추가를 하지 않는다.
````

- [ ] **Step 2: 루트 `CLAUDE.md` 교체**

`CLAUDE.md` 전체 내용:

```markdown
# CLAUDE.md

@AGENTS.md

## Claude Code 전용 규칙

폐쇄망 로컬 에이전트는 이 절을 쓰지 않는다. 공통 규칙은 모두 `AGENTS.md`에 있다.

- 프론트 작업 시 `recruit_front/AGENTS.md`를 직접 읽는다(자동 로드되지 않음). 백엔드 하위에서 작업하면 `recruit_back/recruit_backend/CLAUDE.md`가 함께 로드된다.
- superpowers 설계·계획 문서는 `docs/superpowers/specs/`, `docs/superpowers/plans/`에 쓰고, 작업이 끝나면 `docs/archive/superpowers/`로 옮긴다.
- HTML 리포트는 `design-report` 스킬로 `docs/archive/reports/`에 저장한다.
- 홈 디렉터리(`C:/Users/roehf`)에는 별개의 git 저장소가 있다. 홈에서 git 명령을 실행하지 않는다.
```

- [ ] **Step 3: 점검**

Run: `node tools/check-docs.mjs | grep "^AGENTS.md"`
Expected: 출력 없음(경로 없음·크기 오류 0건).

---

## Task 5: 백엔드 `AGENTS.md` 재작성과 `CLAUDE.md` 축소

현재 백엔드 `CLAUDE.md`는 `AGENTS.md`의 상위 호환(Git 금지 §4.4, Phase 05c 절, 문서화 규칙 추가)이다. 둘을 하나로 합치고, `docs/codex/01`·`04`에서 **현행 코드와 일치하는** 규칙만 흡수한다.

**Files:**
- Modify: `recruit_back/recruit_backend/AGENTS.md` (전체 재작성, 한국어, 15KB 권장·20KB 상한)
- Modify: `recruit_back/recruit_backend/CLAUDE.md` (전체 교체)

**입력 자료** (모두 `recruit_back/recruit_backend/` 기준):
- `CLAUDE.md` (현행, 병합 기준)
- `docs/codex/01-project-context.md` §2 기술 스택, §7 설정 기준, §8 빌드/테스트 명령, §9 구현 스타일 요약, §10 기존 이슈
- `docs/codex/04-implementation-guide.md` §4~§13
- 사실 확인용: `build.gradle`, `src/main/resources/application*.y*ml`, `{BE}/config/SecurityConfig.java`, `{BE}/config/WebMvcConfig.java`, `{BE}/security/auth/RoleNames.java`, `{BE}/exception/GlobalExceptionHandler.java`, `{BE}/dto/response/ApiResponse.java`

- [ ] **Step 1: 새 `AGENTS.md`를 아래 절 구성으로 작성**

| 절 | 내용 | 출처 |
|---|---|---|
| 1. 개요 | 스택 한 줄(Spring Boot 4.0.x · Java 17 · JPA · Spring Security 세션+LDAP · Gradle). 이 디렉터리에는 백엔드만 둔다(Vue·정적 리소스·프론트 빌드 산출물 생성 금지). 읽기 순서: 루트 `AGENTS.md` → `docs/domains/_index.md` → 카드 → 이 문서 | CLAUDE.md 머리말, `build.gradle` |
| 2. 패키지 구조와 공통 기반 | 레이어별 평면 패키지(`controller`·`service`·`domain/entity`·`domain/repository`·`dto/{request,response,condition}`·`enumeration`·`exception`·`security/auth`·`config`·`common/{crypto,hash,util}`) 각 한 줄 책임. 공통 기반: `ApiResponse`, `GlobalExceptionHandler`, `BaseEntity`, `AesAttributeConverter`, `WebMvcConfig`(`/api` 접두), `CorrelationIdFilter`, `TimeConfig` | CLAUDE §5.1, 01 §5, 실제 `{BE}` 디렉터리 |
| 3. 코드 스타일 | Entity·DTO·Service·Controller 규칙 | CLAUDE §5.2~5.5, 04 §4~§7, 01 §9 |
| 4. API 경로 규칙 | `/api` 접두는 `WebMvcConfig`가 붙인다. 관리자 `/admin/...`, 지원자, 면접관 `/interviewer/...`, 공개 조회 규칙 | 04 §8, 실제 컨트롤러 `@RequestMapping` |
| 5. 예외 처리 | 커스텀 예외 명명(`Invalid*Exception`, `*NotFoundException`)과 `GlobalExceptionHandler` 매핑, 401/403 구분 | 04 §9, `GlobalExceptionHandler` |
| 6. 인증·인가 | 세션 인증, 지원자/관리자/면접관 인증 경로, 역할 이름(`RoleNames`), `SecurityConfig` 경로 규칙 요약 | CLAUDE §6, 04 §10 (LDAP 실제 값 금지) |
| 7. 개인정보·암호화 | 암호화 대상 필드와 `AesAttributeConverter`, 해시, 테스트 데이터 규칙 | 04 §11 |
| 8. 설정·실행 | `application.yaml` 핵심 키, 환경변수 표(LDAP 변수는 **이름·부류·기본값만**, 실제 값 금지), `AES_SECRET_KEY` 로컬 예시 키 `22791194512954214612461221261067`(예시임을 명시), `--spring.config.additional-location` 외부 설정, `bootRun`, 포트 | 01 §7~§8, 실제 `application*.y*ml` |
| 9. 테스트 | 수정 범위만 실행(명령은 루트 `AGENTS.md` 5절), `@DataJpaTest`·서비스·보안 테스트 방식, 한글 테스트명, 테스트 JVM 힙 설정 | CLAUDE §10, 04 §12, `build.gradle` test 블록 |
| 10. 금지 사항 | 원본 Excel·보안·구조 변경·git 금지 | CLAUDE §4.1~§4.4 |
| 11. 빌드 실패 대응 | 원인 분류 순서 | 04 §13, CLAUDE §11 |
| 12. 보고 형식 | 변경 파일·테스트 결과·남은 이슈 | CLAUDE §12에서 "문서 갱신(Markdown/HTML/구현 이력)" 줄 제거 |

**제거할 것:** CLAUDE §1(docs/codex 읽기 순서), §3(최근 정리된 상태), §8(구현 우선순위 원칙), §9(Phase 05c 이후 구현 기준), §11 중 Phase/Slice 관련 줄, "Implementation Documentation Rules", "Documentation Output Rule", `docs/codex`·`Codex`·`Claude Code`라는 단어, 04 §14~§16, 01 §0·§3·§4·§6·§11.

**경로 표기 규칙:** 백틱 안의 파일 경로는 레포 루트 기준이나 `{BE}`·`{BT}`·`{BR}` 표기로 쓴다. 백엔드 디렉터리 기준의 `docs/adr/...`·`docs/ops/...`는 점검 스크립트가 레포 루트의 `docs/`로 해석해 오류를 낸다. `recruit_back/recruit_backend/docs/adr/...`로 쓴다.

**사실 확인 규칙:** 흡수하는 문장마다 코드로 확인한다. 예: 01 §10 "기존 이슈" 6개 항목은 각각 현재 코드에서 여전히 참인지 확인해 참인 것만 남긴다. 04의 규칙이 실제 코드 관례와 다르면 **코드 관례**를 적는다.

- [ ] **Step 2: 백엔드 `CLAUDE.md` 교체**

`recruit_back/recruit_backend/CLAUDE.md` 전체 내용:

```markdown
# CLAUDE.md

@AGENTS.md
```

- [ ] **Step 3: 점검**

Run: `node tools/check-docs.mjs | grep "recruit_back/recruit_backend/AGENTS.md"`
Expected: 출력 없음. 15KB 권장 초과 경고는 허용하되 20KB 상한 오류면 3절(코드 스타일)을 요약해 줄인다.

Run: `grep -nE "docs/codex|Codex|Implementation Documentation" recruit_back/recruit_backend/AGENTS.md`
Expected: 출력 없음.

---

## Task 6: 프론트 `AGENTS.md` 정리

**Files:**
- Modify: `recruit_front/AGENTS.md` (15KB 권장·20KB 상한)

기존 절은 영어로 두고(불필요한 번역 금지), 새로 쓰는 절은 한국어로 쓴다. `src/...` 형태의 경로는 프론트 디렉터리 기준으로 읽히며 점검 스크립트 대상이 아니다. `docs/`로 시작하는 경로를 쓸 때는 레포 루트 기준(`docs/domains/_index.md`)으로 쓴다.

- [ ] **Step 1: 절 정리**

| 절 | 처리 |
|---|---|
| Project Overview | "셋업·정상화가 1차 목표" 문장을 삭제하고, 운영 중인 채용 사이트 프론트(지원자 `/applicant`·관리자 `/admin`)라고 현재 상태로 다시 쓴다 |
| Current Project State | 삭제 |
| Confirmed Tech Stack | 유지. `recruit_front/package.json` 기준 주 버전을 덧붙인다(Vue 3.5, Vite 8, TypeScript 6, vue-router 5, Pinia 3, ant-design-vue 4, Axios 1) |
| Main Objectives | 삭제 |
| Package Manager Rules | "npm 사용(`package-lock.json`)" 한 줄로 축소 |
| Common Commands | `package.json` scripts 실제 목록으로 교체: `dev`, `build`(type-check 포함), `type-check`, `test:unit`, `lint`, `format` |
| Setup and Validation Flow | 삭제. 대신 "검증: `npm run type-check` 기본, 필요 시 `npm run build`·`npm run test:unit`" 한 단락 |
| Documentation Rules | "코드 변경 시 도메인 카드 갱신은 루트 `AGENTS.md` 6절을 따른다" 한 단락으로 교체 |
| Node Version | `package.json` engines(`^20.19.0 \|\| >=22.12.0`) 명시 |
| Definition of Done | 셋업 항목(Dependencies install, Vite configuration is valid 등)을 빼고 type-check 통과·기존 라우트/스토어 보존·한글 UI 보존·카드 갱신으로 교체 |
| Recommended First Task | 삭제 |
| 나머지 절 | 유지 (TypeScript/UI/Dependency/Source Preservation/Vue/Routing/Store/API/Env/Path Alias/Styling/Assets/Korean Text/Build Error/Error Handling/Runtime Error/Security/Git/Prohibited/Response) |

- [ ] **Step 2: 새 절 추가 (한국어, Project Overview 바로 다음)**

"## 읽기 순서와 디렉터리 구조" 절:
- 읽기 순서: 루트 `AGENTS.md` → `docs/domains/_index.md` → 카드 → 이 문서.
- 구조: `src/views/admin/<도메인>/`, `src/views/applicant/`, `src/views/auth/`, `src/api/`(루트·`admin/`·`application/sections/`), `src/types/`(api 구조를 따름), `src/stores/`, `src/routes/`(`adminRoutes.ts`·`applicantRoutes.ts`·`authRoutes.ts`·`index.ts` 가드), `src/layouts/`, `src/common/`, `src/components/`. 각 한 줄.
- 공통 기반 파일: `src/api/client.ts`(Axios 인스턴스), `src/api/apiError.ts`, `src/routes/index.ts`(라우트 가드), `src/layouts/AdminLayout.vue`·`ApplicantLayout.vue`, `src/stores/uiStore.ts`, `src/plugins/clientErrorHandlers.ts`. 각 파일을 열어 역할을 한 줄로 확인해 적는다.

- [ ] **Step 3: 점검**

Run: `node tools/check-docs.mjs | grep "recruit_front/AGENTS.md"`
Expected: 출력 없음.

- [ ] **Step 4: 커밋 체크포인트 (요청 시에만)**

```bash
git add AGENTS.md CLAUDE.md docs/domains/_index.md recruit_back/recruit_backend/AGENTS.md recruit_back/recruit_backend/CLAUDE.md recruit_front/AGENTS.md recruit_back/recruit_backend/docs/ops
git commit -m "docs: 로컬 에이전트용 AGENTS.md 진입점과 도메인 색인 추가"
```

---

## 카드 작성 공통 절차 (Task 7~21에 모두 적용)

각 카드 태스크는 아래 C1~C9를 그 태스크의 "입력"으로 수행한다.

**C1. 카드 골격.** `docs/domains/<card>.md`를 만들고 `_index.md` "카드 템플릿"의 제목 구조를 그대로 쓴다. 머리 인용문 두 줄(경로 표기, 관련 카드)을 채운다.

**C2. 백엔드 추적.** 태스크의 "소유 컨트롤러"를 모두 연다. 각 컨트롤러가 주입하는 서비스 → 서비스가 쓰는 엔티티·리포지토리·DTO·enum·예외를 import로 따라간다. 다른 카드가 소유한 클래스(태스크의 "경계" 참고)는 파일 지도에 넣지 않고 규칙·관련 카드에서 링크만 한다.

**C3. 테스트 찾기.** `{BT}`에서 C2 클래스 이름을 검색한다.
```bash
grep -rlE "<클래스1>|<클래스2>" recruit_back/recruit_backend/src/test/java
```

**C4. 프론트 추적.** 태스크의 "소유 화면"을 열고 import를 따라 api 모듈·타입·store·컴포넌트·route name을 찾는다. 공유 모듈(예: `{FE}/api/boardApi.ts`)은 여러 카드 파일 지도에 적어도 된다(점검 대상은 Controller와 `views/**/*.vue`뿐).

**C5. 파일 지도 작성.**
- `### 백엔드` 표: `| 레이어 | 파일 | 역할 |`. 레이어 값: controller / service / entity / repository / dto / enum / exception / config / test. 파일은 `{BE}`·`{BT}` 표기 경로를 백틱으로 적는다. 역할은 한 줄.
- `### 프론트` 표: `| 구분 | 파일 | 역할 |`. 구분 값: route / view / component / api / types / store / test.
- 소유 컨트롤러·소유 화면은 **반드시** 여기에 경로로 적는다. 글롭(`{FE}/views/admin/stageResult/*`)은 매칭 파일 전부를 소유하므로 다른 카드 파일이 섞인 디렉터리에는 쓰지 않는다.
- DTO가 10개를 넘으면 `{BE}/dto/request/StageResult*` 같은 글롭 한 줄로 묶어도 된다(글롭은 매칭 1개 이상이어야 함).

**C6. API 계약 작성.**
- 표: `| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |`. 소유 컨트롤러의 모든 `@*Mapping`을 한 행씩. 경로는 `/api` 생략, 클래스 `@RequestMapping` 접두를 합친 전체 경로.
- 태스크의 "api-contract 섹션"을 `api-contract.md`에서 제목으로 찾아 읽고, 그 섹션의 상태(🟢/🟡/🔴/⛔)·날짜·필드 요약·세부 규칙을 이관한다. 표에 다 안 들어가는 세부는 `### 엔드포인트 상세`에 불릿으로 옮긴다. **세부 규칙을 버리지 않는다.**
- api-contract 섹션이 없는 엔드포인트: 상태 🟢. 프론트에서 호출하지 않으면 `### 엔드포인트 상세`에 "FE 미사용"이라고 적는다.
- 상태는 api-contract 값을 그대로 보존한다. 코드와 계약이 다르면 상태를 🔴로 바꾸고 차이를 `### 엔드포인트 상세`에 적고 최종 보고 목록에 추가한다. 스스로 🟢로 올리지 않는다.
- 권한은 `SecurityConfig` 경로 규칙과 컨트롤러 애너테이션으로 확인한다(예: `ADMIN`, 지원자 세션, 면접관, 공개).

**C7. 규칙·불변식.** 서비스 코드의 검증·상태 전이·예외 조건에서 뽑는다. 각 규칙 끝에 강제 위치를 `({BE}/service/X.java — methodName)` 형식으로 적는다. 태스크의 "참고 문서"(옛 설계·ADR·CONTEXT.md)는 **왜**를 보충할 때만 쓴다. 옛 문서에만 있고 코드에서 확인되지 않는 규칙은 적지 않고 최종 보고 목록에 추가한다.

**C8. 변경 레시피·검증·함정.**
- `## 변경 레시피`: 이 도메인에서 자주 할 작업 2~4개를 `### <작업명>` + 번호 체크리스트(수정 파일 순서, 테스트, 카드 갱신, `node tools/check-docs.mjs`)로 쓴다. 예: "필드 추가", "엔드포인트 추가", "엑셀 컬럼 추가".
- `## 검증`: 이 도메인 테스트만 도는 백엔드 명령(Windows·Linux 둘 다)과 프론트 명령. 예: `--tests "com.shinyoung.recruit.service.StageResult*"`.
- `## 함정·결정`: 아래 명령으로 최근 fix 커밋을 훑어 재발 위험이 있는 것을 한 줄씩 적는다. ADR은 `recruit_back/recruit_backend/docs/adr/<파일>` 경로를 백틱으로 적고 요지 한 줄.
```bash
git log --oneline -n 40 -- <소유 컨트롤러 경로들> <소유 화면 경로들> | grep -iE "fix|revert"
```
- `docs/archive/`나 `docs/codex/` 경로는 카드에 적지 않는다.

**C9. 점검.**
```bash
node tools/check-docs.mjs | grep -E "docs/domains/<card>.md|<소유 컨트롤러 파일명1>|<소유 화면 파일명1>"
```
Expected: 출력 없음(이 카드의 경로 오류 0건, 소유 파일의 누락·중복 0건). 크기가 30KB를 넘으면 표 역할 설명을 줄이고, 40KB를 넘으면 사용자에게 분할 여부를 묻는다.

---

## Task 7: 카드 `auth-account` (인증·지원자 계정)

**Files:** Create `docs/domains/auth-account.md`

- **소유 컨트롤러:** `{BE}/controller/AuthController.java`, `{BE}/controller/ApplicantSignUpController.java`, `{BE}/controller/ApplicantAccountController.java`
- **추가 백엔드 시작점:** `{BE}/security/auth/*`(7개), `{BE}/config/SecurityConfig.java`, `{BE}/config/AuthenticationConfig.java`, `{BE}/config/LdapProperties.java`, `ApplicantSignUpService`, `ApplicantAccountService`, `CurrentApplicantService`, `CurrentEmployeeService`, 엔티티 `User`·`Applicant`·`Employee`
- **소유 화면:** `{FE}/views/auth/LoginView.vue`, `{FE}/views/auth/pop-up/NiceAuthPopup.vue`, `{FE}/views/applicant/SignupView.vue`, `{FE}/views/applicant/AccountRecovery.vue`, `{FE}/views/applicant/ApplicantProfile.vue`
- **프론트 시작점:** `{FE}/api/authApi.ts`, `{FE}/stores/authStore.ts`, `{FE}/stores/__tests__/authStore.spec.ts`, `{FE}/types/auth.ts`, `{FE}/routes/authRoutes.ts`, `{FE}/routes/index.ts`(가드)
- **api-contract 섹션:** 없음 (코드로 작성)
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-03e-admin-auth-hardening-design.md`, `.../design/phase-05y-applicant-account-hardening-design.md`, `.../implementation/fix-auth-status-codes-401-403.md`
- **경계:** `UserRoleMapping`·`DeptRoleMapping`·메뉴는 role-menu. 역할 이름 상수 `RoleNames`는 이 카드 소유.
- **필수 규칙:** 세션 인증 방식, 관리자(LDAP)·지원자(로컬 계정) 인증 경로 분기(`RoutingAuthenticationProvider`), 401/403 응답 구분, LDAP 미설정 시 동작. LDAP 실제 값은 쓰지 않는다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/auth-account.md|AuthController|ApplicantSignUpController|ApplicantAccountController|LoginView|NiceAuthPopup|SignupView|AccountRecovery|ApplicantProfile`

---

## Task 8: 카드 `role-menu` (권한·메뉴)

**Files:** Create `docs/domains/role-menu.md`

- **소유 컨트롤러:** `{BE}/controller/AdminRoleMappingController.java`, `{BE}/controller/MenuController.java`
- **추가 백엔드 시작점:** `RoleMappingService`, `MenuService`, 엔티티 `Menu`·`DeptRoleMapping`·`UserRoleMapping`, enum `MenuSite`·`MenuType`
- **소유 화면:** `{FE}/views/admin/MenuManageView.vue`, `{FE}/views/admin/RoleMappingView.vue`
- **프론트 시작점:** `{FE}/api/menuApi.ts`, `{FE}/api/adminRoleMappingApi.ts`, `{FE}/stores/menuStore.ts`, `{FE}/stores/__tests__/menuStore.spec.ts`, `{FE}/types/menu.ts`, `{FE}/types/roleMapping.ts`, `{FE}/layouts/AdminSidebar.vue`, `{FE}/layouts/ApplicantHeader.vue`, `{FE}/common/antIcon.ts`
- **api-contract 섹션:** "화면: 메뉴 (Menu — 헤더/사이드바, 관리자 메뉴관리)", "화면: 관리자 권한 관리 (RoleMappingView)"
- **참고 문서:** `docs/superpowers/specs/2026-06-30-menu-icon-field-design.md`, `docs/superpowers/specs/2026-08-13-role-mapping-admin-design.md`, ADR `0007-privacy-admin-role-separation.md`
- **수동 DDL:** `recruit_back/recruit_backend/docs/ops/role-mapping-user-role-mapping-ddl.sql`, `recruit_back/recruit_backend/docs/ops/fix-employee-dept-name-unique-drop.sql`
- **경계:** `RoleNames`·인증은 auth-account(링크). `{FE}/views/samples/MenuIconPreview.vue`는 카드 없는 파일.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/role-menu.md|AdminRoleMappingController|MenuController|MenuManageView|RoleMappingView`

---

## Task 9: 카드 `job-posting` (공고·직무·근무지·이미지·첨부 요건)

**Files:** Create `docs/domains/job-posting.md`

- **소유 컨트롤러:** `{BE}/controller/JobPostingController.java`, `{BE}/controller/JobPostingPublicController.java`, `{BE}/controller/JobPostingImageController.java`, `{BE}/controller/JobPostingAttachmentRequirementController.java`
- **추가 백엔드 시작점:** `JobPostingService`, `JobPostingPublicService`, `JobPostingImageService`, `JobPostingImageStorageService`, `ImageSignatureValidator`, `PostingImageResource`, `StoredPostingImageFile`, `JobPostingAttachmentRequirementService`, 엔티티 `JobPosting`·`JobPosition`·`JobPositionWorkLocation`·`JobPostingImage`·`JobPostingAttachmentRequirement`, `{BE}/config/JobPostingImageProperties.java`, enum `JobPostingStatus`·`JobPostingType`·`JobPositionApplicationType`·`EmploymentType`
- **소유 화면:** `{FE}/views/admin/jobPosting/*`, `{FE}/views/applicant/ApplicantRecruit.vue`, `{FE}/views/applicant/ApplicantRecruitList.vue`, `{FE}/views/applicant/ApplicationDetailView.vue`
- **프론트 시작점:** `{FE}/components/jobPosting/JobPostingImageStack.vue`, `{FE}/api/adminJobPostingApi.ts`(2710beb에서 `api/admin/adminJobPostingApi.ts`를 통합), `{FE}/api/boardApi.ts`(공개 공고 조회 함수), `{FE}/types/jobPosting.ts`, `{FE}/types/admin/jobPosting.ts`, `{FE}/types/duty.ts`
- **api-contract 섹션:** "화면: 관리자 공고 등록/수정 + 공고 이미지 (JobPostingImage)", "화면: 직무별 근무지 선택 (공고 상세 ApplicationDetailView + 관리자 공고 등록 AdminJobPostingFormView)"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-03j-job-posting-domain-expansion-design.md`, `recruit_back/recruit_backend/docs/instructions/phase-03j-job-posting-domain-expansion-design-instruction.md`, `docs/superpowers/specs/2026-08-12-job-posting-image-input-design.md`
- **경계:** 공고 질문은 question, 전형은 stage-result, 지원서 양식은 application-form, 통계는 statistics(모두 링크).
- **필수 규칙 (Claude memory에서 이관 — 코드로 확인 후 기재):**
  - 공고유형은 화면상 신입/경력 2종. 신입 = `PUBLIC_RECRUITMENT`, 경력 = `EXPERIENCED_RECRUITMENT`. enum은 바꾸지 않고 기존 값을 재사용한다.
  - `INTERN_RECRUITMENT`·`ROLLING_RECRUITMENT`는 레거시다. enum에는 남아 있지만 관리자 등록 선택지에서 제거됐다.
  - 새 화면에서도 "공개채용/수시채용" 라벨을 되살리지 않는다.
  - 지원자 공고 리스트는 마감(`CLOSED`) 공고를 보여주지 않는다.
  - 학기별 성적 분기(신입만)는 application 카드에 적고 여기서는 링크한다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/job-posting.md|JobPosting(Public|Image|AttachmentRequirement)?Controller|views/admin/jobPosting|ApplicantRecruit|ApplicationDetailView`

---

## Task 10: 카드 `question` (질문 템플릿·공고별 질문)

**Files:** Create `docs/domains/question.md`

- **소유 컨트롤러:** `{BE}/controller/QuestionTemplateController.java`, `{BE}/controller/JobPostingQuestionController.java`
- **추가 백엔드 시작점:** `QuestionTemplateService`, `JobPostingQuestionService`, 엔티티 `QuestionTemplate`·`JobPostingQuestion`, enum `QuestionAnswerType`·`QuestionCategory`
- **소유 화면:** `{FE}/views/admin/applicant/*`, `{FE}/views/admin/applicationForm/ApplicationFormQuestionTab.vue`, `{FE}/views/admin/applicationForm/questionModal/*`
- **프론트 시작점:** `{FE}/types/question.ts` 및 위 화면이 import하는 api 모듈(추적해서 확정)
- **api-contract 섹션:** "화면: 관리자 질문 템플릿 (전역 질문 은행)", "화면: 공고별 질문 구성 (관리자 — 공고 질문)"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-03c-9-question-answer-design.md`(질문 쪽만)
- **경계:** 지원자 답변(`ApplicationAnswerController`)은 application. `ApplicationFormQuestionTab.vue`는 application-form 화면의 탭이지만 이 카드가 소유한다. `{FE}/views/admin/applicationForm/*` 글롭을 쓰지 않는다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/question.md|QuestionTemplateController|JobPostingQuestionController|views/admin/applicant|ApplicationFormQuestionTab|questionModal`

---

## Task 11: 카드 `application-form` (지원서 양식 설정)

**Files:** Create `docs/domains/application-form.md`

- **소유 컨트롤러:** `{BE}/controller/AdminApplicationFormController.java`, `{BE}/controller/AdminApplicationFormConfigController.java`, `{BE}/controller/AdminApplicationFormLayoutController.java`
- **추가 백엔드 시작점:** `ApplicationFormConfigService`, `ApplicationFormLayoutService`, `ApplicationFormLayoutDefaultFactory`, `ApplicationFormLayoutSectionPolicy`, `ApplicationFormLayoutValidator`, `ApplicationFormPageService`, `AdminApplicationFormSummaryService`, `ApplicationFormEditWindow`, 엔티티 `ApplicationFormConfig`·`ApplicationFormPage`·`ApplicationFormPageItem`, `{BE}/domain/repository/ApplicationFormLayoutItemView.java`, enum `ApplicationFormConfigState`·`ApplicationFormRequirementType`·`ApplicationSectionType`
- **소유 화면 (개별 파일로 적는다, 글롭 금지):** `{FE}/views/admin/applicationForm/AdminApplicationFormListView.vue`, `{FE}/views/admin/applicationForm/AdminApplicationFormDetailView.vue`, `{FE}/views/admin/applicationForm/ApplicationFormConfigTab.vue`, `{FE}/views/admin/applicationForm/ApplicationFormLayoutTab.vue`
- **프론트 시작점:** `{FE}/api/admin/adminApplicationFormApi.ts`
- **api-contract 섹션:** "화면: 지원서 설정 현황 (AdminApplicationFormListView)", "화면: 공고별 지원서 설정 (AdminApplicationFormDetailView)", "변경: 공고 등록/수정에서 지원서 양식 분리" — 셋 다 🟡 초안, 상태 보존
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/application-form-page-layout-design-note.md`, `.../design/phase-05-application-form-page-layout-design.md`, `.../design/phase-03k-application-form-required-policy-design.md`, `docs/superpowers/specs/2026-07-06-form-page-posting-type-design.md`
- **경계:** 지원자 쪽 "지원서 작성 폼 로드" API는 application 카드. `ApplicationFormPageService`가 양쪽에서 쓰이면 이 카드가 소유하고 application에서 링크한다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/application-form.md|AdminApplicationForm(Config|Layout)?Controller|AdminApplicationForm(List|Detail)View|ApplicationForm(Config|Layout)Tab`

---

## Task 12: 카드 `application` (지원서 작성·제출)

**Files:** Create `docs/domains/application.md`

- **소유 컨트롤러:** `{BE}/controller/ApplicationController.java`, `{BE}/controller/ApplicationBasicInfoController.java`, `{BE}/controller/ApplicationEducationController.java`, `{BE}/controller/ApplicationCareerController.java`, `{BE}/controller/ApplicationCertificateController.java`, `{BE}/controller/ApplicationLanguageController.java`, `{BE}/controller/ApplicationMilitaryController.java`, `{BE}/controller/ApplicationAwardController.java`, `{BE}/controller/ApplicationGapPeriodController.java`, `{BE}/controller/ApplicationAnswerController.java`
- **추가 백엔드 시작점:** `JobApplicationService`, 섹션 서비스(`ApplicationBasicInfoService`·`ApplicationEducationService`·`ApplicationCareerService`·`ApplicationCertificateService`·`ApplicationLanguageService`·`ApplicationMilitaryService`·`ApplicationAwardService`·`ApplicationGapPeriodService`·`ApplicationAnswerService`), `ApplicationSectionAccessService`, `ApplicationSubmitValidator`, `ApplicationCompletionReadChecker`, `ApplicationDashboardService`, 엔티티 `JobApplication`·`ApplicationBasicInfo`·`ApplicationEducation`·`ApplicationEducationSemesterGrade`·`ApplicationCareer`·`ApplicationCertificate`·`ApplicationLanguage`·`ApplicationMilitary`·`ApplicationAward`·`ApplicationGapPeriod`·`ApplicationAnswer`, enum `JobApplicationStatus` 등
- **소유 화면:** `{FE}/views/applicant/ApplicationFormView.vue`, `{FE}/views/applicant/application/sections/*`
- **프론트 시작점:** `{FE}/api/applicationApi.ts`, `{FE}/api/__tests__/applicationApi.spec.ts`, `{FE}/api/application/dashboardApi.ts`, `{FE}/api/application/sections/*`(`attachmentApi.ts` 제외 — attachment 카드), `{FE}/types/application.ts`, `{FE}/types/application/`, `{FE}/common/applicationSection.ts`, `{FE}/views/applicant/application/useSectionDraftState.ts`, `{FE}/views/applicant/application/__tests__/useSectionDraftState.spec.ts`
- **api-contract 섹션:** "지원자 경력", "지원자 학력", "지원자 어학", "지원자 수상", "지원자 자격증", "지원자 공백기간", "지원자 자기소개/질문", "지원자 기본정보 — 보훈", "지원자 병역", "지원서 작성 완성도", "지원서 작성 폼 로드", "공고 상세 — 기지원 여부 확인 (ApplicationDetailView)" (각 "화면: " 접두 제목). 마지막 섹션은 화면이 job-posting 소유이므로 job-posting 카드에서 링크한다(엔드포인트 소유 컨트롤러로 최종 판단).
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/` 중 `phase-03-application-design.md`, `phase-03c-application-detail-design.md`, `phase-03c-9-question-answer-design.md`, `phase-03h-applicant-my-applications-design.md`, `phase-03h-3-applicant-application-dashboard-design.md`; `recruit_back/recruit_backend/docs/superpowers/specs/2026-06-12-application-basic-info-design.md`; `docs/superpowers/specs/` 중 `2026-06-23-application-sections-language-award-certificate-design.md`, `2026-06-23-career-school-backend-cleanup-design.md`, `2026-06-23-language-conversation-veteran-type-design.md`, `2026-06-24-application-sections-gap-period-question-answer-design.md`, `2026-06-25-career-education-field-changes-design.md`, `2026-06-30-education-overall-gpa-design.md`
- **경계:** 학교 검색 모달(`SchoolModalBody.vue`)·주소 검색(`addressApi.ts`)은 master-data, 첨부는 attachment, 전형결과 조회는 stage-result, 양식 설정은 application-form(모두 링크).
- **필수 규칙:** 학기별 성적은 신입(`PUBLIC_RECRUITMENT`) 공고만 받고 경력은 평균 성적만 받는다(코드에서 분기 위치 확인 후 기재). 제출 검증(`ApplicationSubmitValidator`) 항목 전체.
- **크기 주의:** 가장 큰 카드다. 섹션별 상세는 표 중심으로 짧게 쓴다. 40KB를 넘으면 `application.md`(생성·제출·대시보드·폼 로드)와 `application-sections.md`(섹션 9종)로 나누자고 사용자에게 제안하고, 승인 시 `_index.md` 카드 목록·역색인과 설계서 6.1을 함께 고친다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/application.md|controller/Application(BasicInfo|Education|Career|Certificate|Language|Military|Award|GapPeriod|Answer)?Controller|ApplicationFormView|application/sections`

---

## Task 13: 카드 `attachment` (첨부 저장소)

**Files:** Create `docs/domains/attachment.md`

- **소유 컨트롤러:** `{BE}/controller/ApplicationAttachmentController.java`, `{BE}/controller/AdminApplicationAttachmentController.java`, `{BE}/controller/AdminAttachmentStorageHealthController.java`
- **추가 백엔드 시작점:** `{BE}/controller/AttachmentDownloadResponseFactory.java`, `ApplicationAttachmentService`·`ApplicationAttachmentFileService`·`ApplicationAttachmentDeleteService`·`ApplicationAttachmentDownloadService`, `AttachmentStorageService`, `LocalAttachmentStorageService`, `AttachmentStorageResource`, `AttachmentStorageDeleteResult`, `AttachmentFilePolicy`, `AttachmentAdminMetadata`, `AttachmentDownloadResource`, `StoredAttachmentFile`, `AttachmentStorageHealthScanService`, 엔티티 `ApplicationAttachment`, `{BE}/config/AttachmentProperties.java`, enum `AttachmentType`·`AttachmentDeleteActorType`·`AttachmentStorageHealthIssueType`·`PhysicalFileStatus`
- **소유 화면:** 없음 (지원서 섹션 내부에서 사용)
- **프론트 시작점:** `{FE}/api/application/sections/attachmentApi.ts`, `{FE}/types/application/sections/attachment.ts`, 사용처 `BasicInfoSection.vue`·`CareerSection.vue`
- **api-contract 섹션:** "화면: 지원자 첨부파일 (ApplicationAttachment — 독립 섹션) ⛔ 프론트 폐지 (2026-09-01)" — ⛔ 상태와 폐지 경위를 보존하고, 현재 섹션 내부 첨부로 쓰이는 엔드포인트는 코드 기준으로 상태를 적는다
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-03i-attachment-file-upload-download-design.md`, `.../design/phase-03i-4-attachment-delete-cleanup-repair-design.md`, `.../design/phase-03i-5-attachment-required-policy-design.md`, ADR `0005-retention-purge-mode-tombstone-anonymization-binary-deletion.md`
- **경계:** 첨부 요건 정책(공고별)은 job-posting, 파기 saga(`AttachmentPurgeSagaService`)는 privacy-audit, 관리자 지원서 상세의 첨부 목록 조회(`AdminApplicationSectionController`)는 admin-application(모두 링크).
- **필수 규칙:** 저장 경로·파일명 정책, 허용 확장자·크기, 삭제 시 논리/물리 삭제 순서, 다운로드 헤더(`Content-Disposition`, `Cache-Control: no-store`).

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/attachment.md|controller/(Admin)?(Application)?Attachment|AttachmentStorageHealthController`

---

## Task 14: 카드 `admin-application` (지원현황·상세·엑셀/PDF)

**Files:** Create `docs/domains/admin-application.md`

- **소유 컨트롤러:** `{BE}/controller/AdminApplicationController.java`, `{BE}/controller/AdminApplicationSectionController.java`, `{BE}/controller/AdminExportController.java`, `{BE}/controller/ApplicationPdfController.java`
- **추가 백엔드 시작점:** `{BE}/controller/ExcelExportResponseFactory.java`, `AdminApplicationSearchConditionFactory`, `AdminApplicationSectionService`, `AdminDatasetExportService`, `AdminStageResultEnricher`, `ApplicationExportColumn`·`ApplicationExportRowAssembler`·`ApplicationExportSection`·`ApplicationExportService`, `Excel*`·`Export*` 서비스 클래스, `ApplicationPdf*`, `ApplicationPhotoLoader`, `PdfAuditLogger`, `PdfMetadata`, `{BE}/dto/condition/`, `{BE}/config/ExportProperties.java`, `{BE}/config/PdfProperties.java`
- **소유 화면:** `{FE}/views/admin/application/*`
- **프론트 시작점:** `{FE}/api/admin/adminApplicationApi.ts`, `{FE}/types/admin/application.ts`, `{FE}/types/admin/applicationSections.ts`, `{FE}/common/fileDownload.ts`
- **api-contract 섹션:** "화면: 지원현황 조회 (관리자 — 지원서 검색)"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-07-export-pdf-statistics-design.md`(export·PDF 부분), `recruit_back/recruit_backend/CONTEXT.md` "Export / Reporting (Phase 07)" 절, `docs/superpowers/specs/2026-09-18-application-export-column-selection-design.md`, ADR `0001-application-pdf-openhtmltopdf-avoid-itext-agpl.md`, ADR `0002-phase07-export-readonly-upload-stageresult-only.md`
- **경계:** 전형결과 업로드는 stage-result, 첨부 다운로드·삭제는 attachment, 통계는 statistics(링크).
- **필수 규칙:** 엑셀 export는 읽기 전용(ADR 0002), 행 수 상한(`ExportRowLimitExceededException`), PDF 일괄 상한(`PdfBulkLimitExceededException`), export·PDF 감사 로깅, 엑셀 컬럼 선택 규칙.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/admin-application.md|controller/Admin(Application|ApplicationSection|Export)Controller|ApplicationPdfController|views/admin/application/`

---

## Task 15: 카드 `stage-result` (전형·전형결과)

**Files:** Create `docs/domains/stage-result.md`

- **소유 컨트롤러:** `{BE}/controller/StageController.java`, `{BE}/controller/StageResultController.java`, `{BE}/controller/StageResultUploadController.java`, `{BE}/controller/ApplicationStageResultController.java`
- **추가 백엔드 시작점:** `StageService`, `StageResultService`, `StageResultCorrectionService`, `StageResultUploadService`, `StageResultUploadParser`, `StageResultStatusLabels`, `StageResultChangeMetadata`, `UploadAuditLogger`, `UploadConflictMetadata`, `UploadMetadata`, `ApplicationStageResultService`, 엔티티 `Stage`·`StageResult`·`StageResultCorrectionHistory`, 관련 enum
- **소유 화면:** `{FE}/views/admin/stageResult/*`
- **프론트 시작점:** `{FE}/api/admin/adminStageApi.ts`, `{FE}/types/admin/stage.ts`, `{FE}/views/admin/stageResult/useStageLifecycle.ts`
- **api-contract 섹션:** "화면: 전형결과 관리 (AdminStageResultView)", "화면: 마이페이지 지원 목록 — 전형결과 확인 (ApplicantProfile)"(화면은 auth-account 소유 — 엔드포인트 소유 컨트롤러로 최종 판단하고 auth-account에서 링크)
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-02-stage-design.md`, `.../design/phase-03d-stage-result-design.md`, `.../design/phase-03d-4-5-result-read-correction-design.md`, `docs/superpowers/specs/2026-09-04-admin-stage-result-management-design.md`, ADR `0002-phase07-export-readonly-upload-stageresult-only.md`
- **수동 DDL:** `recruit_back/recruit_backend/docs/ops/phase-07d-stage-result-version-column.sql`
- **필수 규칙:** 전형 상태 전이, 결과 업로드 미리보기→커밋 흐름과 충돌 처리, 정정 이력, 낙관적 락(version 컬럼 — 수동 DDL 필요), 지원자 결과 조회 노출 조건.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/stage-result.md|controller/(Application)?Stage(Result)?(Upload)?Controller|views/admin/stageResult`

---

## Task 16: 카드 `interview` (면접 일정·평가)

**Files:** Create `docs/domains/interview.md`

- **소유 컨트롤러:** `{BE}/controller/InterviewAdminController.java`, `{BE}/controller/InterviewEvaluationAdminController.java`, `{BE}/controller/InterviewScheduleController.java`, `{BE}/controller/InterviewerEvaluationController.java`, `{BE}/controller/InterviewerInterviewController.java`, `{BE}/controller/ApplicantInterviewController.java`
- **추가 백엔드 시작점:** `InterviewService`, `InterviewScheduleService`, `InterviewScheduleUploadParser`, `InterviewEvaluationAdminService`, `InterviewerEvaluationService`, `InterviewerInterviewService`, `ApplicantInterviewService`, `EvaluationReopenMetadata`, 엔티티 `Interview`·`InterviewParticipant`·`InterviewEvaluation`, enum `Interview*`·`Evaluation*`
- **소유 화면:** `{FE}/views/admin/interview/*`
- **프론트 시작점:** `{FE}/api/admin/adminInterviewApi.ts`, `{FE}/types/admin/interview.ts`
- **api-contract 섹션:** "화면: 면접 스케줄링 (관리자 interviewScheduling.vue)"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-04-interview-scheduling-design.md`, `.../design/phase-06-interview-evaluation-design.md`
- **필수 규칙:** 면접 상태 전이(확정 등), 참가자 역할, 평가 DRAFT/SUBMITTED와 재개, 스케줄 엑셀 업로드 검증, 면접 평가가 전형결과를 바꾸지 않는다는 경계(코드로 확인).

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/interview.md|Interview(Admin|EvaluationAdmin|Schedule)Controller|Interviewer(Evaluation|Interview)Controller|ApplicantInterviewController|views/admin/interview`

---

## Task 17: 카드 `master-data` (공통코드·학교·주소)

**Files:** Create `docs/domains/master-data.md`

- **소유 컨트롤러:** `{BE}/controller/CommonCodeController.java`, `{BE}/controller/AdminCommonCodeController.java`, `{BE}/controller/AdminSchoolController.java`, `{BE}/controller/SchoolSearchController.java`, `{BE}/controller/AddressSearchController.java`
- **추가 백엔드 시작점:** `CommonCodeService`, `CommonCodeNames`, `SchoolService`, `SchoolSearchService`, `SchoolImportService`, `SchoolImportParser`, `NeisSchoolClient`, `UnivDeptSchoolClient`, `UnivInfoSchoolClient`, `PublicDataServiceKey`, `AddressSearchService`, `JusoAddressClient`, `JusoApiResponse`, 엔티티 `CommonCode`·`School`, config `JusoClientConfig`·`JusoProperties`·`NeisProperties`·`SchoolOpenApiClientConfig`·`UnivDeptProperties`·`UnivInfoProperties`
- **소유 화면:** `{FE}/views/admin/AdminCommonCodeManageView.vue`, `{FE}/views/common/SchoolModalBody.vue`
- **프론트 시작점:** `{FE}/api/adminCommonCodeApi.ts`, `{FE}/api/commonApi.ts`, `{FE}/api/application/addressApi.ts`, `{FE}/types/commonCode.ts`, `{FE}/types/application/address.ts`
- **api-contract 섹션:** "화면: 학교 검색 (외부 OpenAPI)", "화면: 주소 검색 (AddressSearch — juso.go.kr 프록시)", "화면: 관리자 공통코드 관리 (AdminCommonCodeManageView)"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-08-commoncode-school-master-design.md`, `recruit_back/recruit_backend/CONTEXT.md` "Master data (Phase 08)" 절, `docs/superpowers/specs/2026-08-28-admin-common-code-manage-design.md`, ADR `0003-commoncode-additive-no-enum-migration.md`, ADR `0004-school-optional-application-level-link.md`
- **필수 규칙:** 공통코드는 추가만 하고 enum 마이그레이션을 하지 않는다(ADR 0003). **함정 절에 폐쇄망 주의를 적는다:** 주소(juso.go.kr)·학교(NEIS·대학 OpenAPI) 검색은 외부 API 의존이다. 설정 키 이름만 적고 실제 API 키 값은 쓰지 않는다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/master-data.md|controller/(Admin)?CommonCodeController|AdminSchoolController|SchoolSearchController|AddressSearchController|AdminCommonCodeManageView|SchoolModalBody`

---

## Task 18: 카드 `board` (FAQ·공지)

**Files:** Create `docs/domains/board.md`

- **소유 컨트롤러:** `{BE}/controller/FaqController.java`, `{BE}/controller/AdminFaqController.java`, `{BE}/controller/BoardController.java`
- **추가 백엔드 시작점:** `FaqService`, `NoticeService`, 엔티티 `Faq`·`FaqCategory`·`Notice`, `{BE}/common/util/HtmlTextUtils.java`(공지 본문 처리에 쓰이는지 확인), enum `NoticeSearchType`
- **소유 화면:** `{FE}/views/admin/faq/*`, `{FE}/views/applicant/FaqView.vue`, `{FE}/views/applicant/NoticeListView.vue`
- **프론트 시작점:** `{FE}/api/faqApi.ts`, `{FE}/api/adminFaqApi.ts`, `{FE}/api/boardApi.ts`(공지 함수), `{FE}/types/faq.ts`, `{FE}/types/notice.ts`
- **api-contract 섹션:** "화면: FAQ (지원자 FaqView / 관리자 AdminFaqManageView)", "화면: 공지사항 (NoticeListView)"

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/board.md|controller/(Admin)?FaqController|BoardController|views/admin/faq|FaqView|NoticeList`

---

## Task 19: 카드 `statistics` (대시보드·통계)

**Files:** Create `docs/domains/statistics.md`

- **소유 컨트롤러:** `{BE}/controller/AdminStatisticsController.java`
- **추가 백엔드 시작점:** `FunnelStatisticsService`, `ApplicationTrendStatisticsService`, `{BE}/domain/repository/JobPositionCountProjection.java`, enum `FunnelDimension`
- **소유 화면:** `{FE}/views/admin/AdminHomeView.vue`, `{FE}/views/admin/dashboard/*`
- **프론트 시작점:** `{FE}/api/statisticsApi.ts`, `{FE}/types/statistics.ts`, `{FE}/common/chartPalette.ts`
- **api-contract 섹션:** "화면: 관리자 대시보드 (AdminHomeView — 시안 2a \"퍼널 중심\")"
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-07-export-pdf-statistics-design.md`(통계 부분), `docs/superpowers/specs/2026-08-11-admin-dashboard-statistics-api-design.md`
- **필수 규칙:** 퍼널 집계 기준(어떤 상태·전형을 세는지), 일별 추이 기간·시간대 기준.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/statistics.md|AdminStatisticsController|AdminHomeView|views/admin/dashboard`

---

## Task 20: 카드 `privacy-audit` (보존·파기·감사)

**Files:** Create `docs/domains/privacy-audit.md`

- **소유 컨트롤러:** `{BE}/controller/AdminRetentionController.java`, `{BE}/controller/AdminAuditController.java`
- **추가 백엔드 시작점:** `ActivityLogService`, `AuditActivityReadService`, `AuditActorContext`, `AuditEvent`, `AuditMetadata`, `AuditRequestContextResolver`, `ApplicationPiiPurgeService`, `AttachmentPurgeSagaService`, `Purge*`·`Retention*` 서비스 클래스, 엔티티 `ActivityLog`·`PurgeBatch`·`PurgeJobItem`·`RetentionHold`·`RetentionPolicy`, `{BE}/domain/repository/ApplicationPiiPurgeRepository.java`, `{BE}/config/AuditConfig.java`, `{BE}/common/hash/AuditHmac.java`, enum `Audit*`·`Purge*`·`ActorType`
- **소유 화면:** 없음 (관리자 API만 존재 — "FE 미사용" 표기)
- **api-contract 섹션:** 없음 (코드로 작성)
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-09-privacy-purge-audit-retention-design.md`, `recruit_back/recruit_backend/CONTEXT.md` "Privacy / Audit (Phase 09)" 절과 "Flagged ambiguities", ADR `0005`·`0006`·`0007`
- **수동 DDL:** `recruit_back/recruit_backend/docs/ops/phase-09a-activity-log-ddl.sql`, `.../phase-09c-retention-ddl.sql`, `.../phase-09d-1-purge-execute-ddl.sql`, `.../phase-09d-2-attachment-saga-ddl.sql`, `.../phase-09e-reconciliation-ddl.sql` (카드에는 전체 경로로 적는다)
- **필수 규칙:** 보존 정책·보류(hold)·dry-run·실행·정합성 점검(reconciliation) 흐름, tombstone·익명화·바이너리 삭제 모드(ADR 0005), 감사 트랜잭션 정책(ADR 0006), 개인정보 관리자 역할 분리(ADR 0007). CONTEXT.md 용어를 `## 용어` 표로 옮긴다.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/privacy-audit.md|AdminRetentionController|AdminAuditController`

---

## Task 21: 카드 `client-event-log` (클라이언트 이벤트 로그)

**Files:** Create `docs/domains/client-event-log.md`

- **소유 컨트롤러:** `{BE}/controller/ClientEventLogController.java`, `{BE}/controller/AdminClientEventLogController.java`
- **추가 백엔드 시작점:** `ClientEventLogService`, `ClientEventLogReadService`, `ClientEventLogCleanupService`, `ClientEventLogCleanupScheduler`, `ClientEventMetadataSanitizer`, `ClientEventRateLimiter`, 엔티티 `ClientEventLog`, enum `ClientEvent*`
- **소유 화면:** 없음
- **프론트 시작점:** `{FE}/api/clientEventApi.ts`, `{FE}/api/telemetryClient.ts`, `{FE}/common/clientEventLogger.ts`, `{FE}/common/clientSession.ts`, `{FE}/common/httpErrorTelemetry.ts`, `{FE}/plugins/clientErrorHandlers.ts`, `{FE}/types/clientEvent.ts`
- **api-contract 섹션:** 없음 (코드로 작성)
- **참고 문서:** `recruit_back/recruit_backend/docs/codex/design/phase-09f-client-event-log-design.md`, `.../design/phase-09f-client-event-log-be-design.md`, `recruit_back/recruit_backend/docs/codex/plans/2026-06-10-phase-09f-client-event-log-be.md`
- **필수 규칙:** 수집 rate limit, 메타데이터 정제(개인정보 제거), 정리 스케줄·보존 기간.

- [ ] **Step 1: C1~C8 수행**
- [ ] **Step 2: C9 점검** — grep 패턴 `docs/domains/client-event-log.md|controller/(Admin)?ClientEventLogController`

- [ ] **카드 완료 후 커밋 체크포인트 (요청 시에만)**

```bash
git add docs/domains
git commit -m "docs: 도메인 카드 15종 작성"
```

---

## Task 22: 이력 문서 archive 이동과 `.ignore`

카드 15개가 모두 끝난 뒤 한 번에 수행한다.

**Files:**
- Move: 아래 목록
- Create: `.ignore`

- [ ] **Step 1: 추적 여부 확인**

```bash
for p in api-contract.md recruit_back/recruit_backend/docs/codex recruit_back/recruit_backend/docs/instructions recruit_back/recruit_backend/docs/superpowers recruit_back/recruit_backend/CONTEXT.md recruit_back/recruit_backend/instruction.md recruit_back/recruit_backend/README-codex-docs.md recruit_back/recruit_backend/HELP.md docs/design; do printf '%s: ' "$p"; git ls-files "$p" | wc -l; done
```

Expected: 모든 항목이 1 이상. 0인 항목(미추적)은 `git mv` 대신 `mv`로 옮기고 최종 보고에 적는다.

- [ ] **Step 2: 이동**

```bash
mkdir -p docs/archive/backend
git mv api-contract.md docs/archive/api-contract.md
git mv recruit_back/recruit_backend/docs/codex docs/archive/backend/codex
git mv recruit_back/recruit_backend/docs/instructions docs/archive/backend/instructions
git mv recruit_back/recruit_backend/docs/superpowers docs/archive/backend/superpowers
git mv recruit_back/recruit_backend/CONTEXT.md docs/archive/backend/CONTEXT.md
git mv recruit_back/recruit_backend/instruction.md docs/archive/backend/instruction.md
git mv recruit_back/recruit_backend/README-codex-docs.md docs/archive/backend/README-codex-docs.md
git mv recruit_back/recruit_backend/HELP.md docs/archive/backend/HELP.md
git mv docs/design docs/archive/design
```

루트 `docs/superpowers/`는 이 계획서가 들어 있으므로 Task 24에서 옮긴다.

- [ ] **Step 3: `.ignore` 작성**

`.ignore` 전체 내용:

```
# ripgrep 기반 검색 도구에서 과거 이력 문서를 제외한다.
# docs/archive/는 작업 근거가 아니다(AGENTS.md 8절). 필요하면 경로를 직접 열어 읽는다.
docs/archive/
```

- [ ] **Step 4: 남은 백엔드 docs 확인**

Run: `ls recruit_back/recruit_backend/docs`
Expected: `adr  ops` 두 개만.

- [ ] **Step 5: 점검**

Run: `node tools/check-docs.mjs; echo "exit=$?"`
Expected: `문서 점검 통과`, `exit=0`. 경로 없음 오류가 나오면 카드·AGENTS가 옮겨진 경로를 가리키는 것이므로 해당 줄을 고친다(archive 경로로 바꾸지 말고 참조 자체를 없애거나 현행 경로로 바꾼다).

- [ ] **Step 6: 커밋 체크포인트 (요청 시에만)**

`git mv`로 옮긴 파일은 이미 스테이징되어 있다. `.ignore`만 추가한다.

```bash
git add .ignore
git status --short
git commit -m "docs: 이력 문서를 docs/archive로 이동"
```

---

## Task 23: 잔여 참조 정리와 교차 검토

**Files:**
- Modify: 필요 시 `AGENTS.md`, 레포 `AGENTS.md`, `docs/domains/*.md`

- [ ] **Step 1: 현행 문서의 오래된 참조 검색**

```bash
grep -nE "docs/codex|api-contract\.md|CONTEXT\.md|instruction\.md|README-codex" AGENTS.md CLAUDE.md recruit_back/recruit_backend/AGENTS.md recruit_back/recruit_backend/CLAUDE.md recruit_front/AGENTS.md recruit_front/README.md docs/domains/*.md
```

Expected: `AGENTS.md` 4절의 "옛 `api-contract.md`는 `docs/archive/`로 옮겨졌다" 한 줄만. 그 외 결과는 해당 문서에서 고친다.

- [ ] **Step 2: 코드·ADR의 오래된 참조 수집 (수정 금지, 보고용)**

```bash
grep -rnE "docs/codex|api-contract" --include=*.java --include=*.ts --include=*.vue recruit_back/recruit_backend/src recruit_front/src
grep -rnE "docs/codex|CONTEXT\.md|instruction\.md" recruit_back/recruit_backend/docs/adr
```

알려진 코드 항목: `{BE}/config/AuthenticationConfig.java:40`(01-project-context 환경변수 표 → 이제 백엔드 `AGENTS.md` 8절), `{BE}/domain/entity/StageResult.java:77`(ops 경로), `{FE}/types/admin/stage.ts:3`, `{FE}/types/roleMapping.ts:1`(api-contract 섹션 → 카드). 결과 전체를 최종 보고의 "오래된 참조" 목록에 적는다.

- [ ] **Step 3: API 표 행 수 대조**

카드별로 소유 컨트롤러의 매핑 애너테이션 수와 API 표 행 수를 비교한다.

```bash
BE=recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit
for card in docs/domains/*.md; do
  [ "$(basename "$card")" = "_index.md" ] && continue
  ctrls=$(sed -n '/^## 파일 지도/,/^## API 계약/p' "$card" | grep -oE '\{BE\}/controller/[A-Za-z]+Controller\.java' | sed "s#{BE}#$BE#" | sort -u)
  maps=0; for c in $ctrls; do n=$(grep -cE '@(Get|Post|Put|Patch|Delete)Mapping' "$c"); maps=$((maps+n)); done
  rows=$(sed -n '/^## API 계약/,/^## 규칙/p' "$card" | grep -cE '^\| *(🟢|🟡|🔴|⛔)')
  echo "$(basename "$card"): 매핑 $maps / 표 $rows"
done
```

Expected: 카드마다 두 수가 같다. 다르면 누락·중복 행을 찾아 고친다. (한 메서드에 경로가 여러 개인 매핑은 예외 — 확인 후 그대로 둔다.)

- [ ] **Step 4: 독립 교차 검토**

카드를 3~4개씩 묶어 검토자(작성자와 다른 subagent)에게 맡긴다. 검토자에게 줄 체크리스트:
1. `## 파일 지도`가 소유 컨트롤러·서비스·엔티티·화면·api 모듈을 빠짐없이 담았는가 (컨트롤러 import 추적으로 확인)
2. `## API 계약`의 메서드·경로가 코드 애너테이션과 일치하는가, api-contract 섹션의 세부 규칙이 빠지지 않았는가 (`docs/archive/api-contract.md`와 대조)
3. `## 규칙·불변식`의 각 규칙이 적힌 코드 위치에서 실제로 강제되는가
4. 변경 레시피의 파일 순서대로 따라 했을 때 빠지는 파일이 없는가
5. 운영 비밀값·실제 LDAP/DB 정보가 없는가

검토 결과로 고친 뒤 `node tools/check-docs.mjs`를 다시 통과시킨다.

- [ ] **Step 5: 최종 점검**

Run: `node tools/check-docs.mjs; echo "exit=$?"` → `exit=0`
Run: `node --test tools/check-docs.test.mjs` → `fail 0`
Run: `wc -c AGENTS.md recruit_back/recruit_backend/AGENTS.md recruit_front/AGENTS.md docs/domains/*.md` → 크기 기록(보고용)

---

## Task 24: 마무리 (계획 문서 이동, 구현 리포트, 보고)

**Files:**
- Move: `docs/superpowers/` → `docs/archive/superpowers/`
- Create: `docs/archive/reports/local-llm-docs-restructure_implementation.html`

- [ ] **Step 1: 설계·계획 문서 이동**

```bash
mkdir -p docs/archive/superpowers
git mv docs/superpowers/specs docs/archive/superpowers/specs
git mv docs/superpowers/plans docs/archive/superpowers/plans
```

Run: `ls docs`
Expected: `archive  domains` 두 개만 (빈 `docs/superpowers`가 남으면 삭제).

- [ ] **Step 2: 점검 재실행**

Run: `node tools/check-docs.mjs; echo "exit=$?"` → `exit=0`

- [ ] **Step 3: 구현 HTML 리포트**

`design-report` 스킬(Skill 도구)로 `docs/archive/reports/local-llm-docs-restructure_implementation.html`을 만든다. 포함 내용: 변경 파일(생성·수정·이동), 점검 스크립트 테스트 결과, 문서별 크기, 카드별 API 표 대조 결과, 오래된 참조 목록, 🔴 표시한 계약 불일치, 코드로 확인되지 않아 뺀 옛 규칙, 미사용 추정 파일. 스킬을 쓸 수 없으면 리포트가 막혔다고 보고한다(직접 만들지 않는다).

- [ ] **Step 4: 사용자 보고 (한국어)**

1. 변경 요약: 생성·수정·이동 파일 수와 주요 경로
2. 검증 결과: `check-docs` 통과 여부, 테스트 결과, 문서 크기
3. 사용자 결정이 필요한 항목: 🔴 계약 불일치, 코드 주석·ADR의 오래된 참조(수정 여부), 카드 작성 중 발견한 미사용 추정 파일
4. 커밋: 아직 커밋하지 않았다면 커밋 여부를 묻는다

- [ ] **Step 5: 최종 커밋 (요청 시에만)**

```bash
git add -A docs tools
git add AGENTS.md CLAUDE.md .ignore recruit_back/recruit_backend/AGENTS.md recruit_back/recruit_backend/CLAUDE.md recruit_front/AGENTS.md
git status --short
git commit -m "docs: 로컬 LLM 유지보수용 문서 재편"
```

커밋 전 `git status --short`에서 코드 파일(`*.java`, `*.ts`, `*.vue`)이 스테이징되지 않았는지 확인한다.

