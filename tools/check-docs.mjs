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
