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

test('sectionText: 펜스 코드 블록 안의 ## 줄에서는 절을 끝내지 않는다', () => {
  const text = '## 파일 지도\n`a`\n```md\n## 가짜\n```\n`b`\n## API 계약';
  assert.equal(sectionText(text, '## 파일 지도'), '`a`\n```md\n## 가짜\n```\n`b`');
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
