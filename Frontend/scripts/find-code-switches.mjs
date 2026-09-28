#!/usr/bin/env node
/**
 * Fails when a component or service hard-codes a business code list.
 *
 * Two shapes are looked for in src/app/**\/*.ts (specs excluded):
 *
 *   1. `case 'UPPER_SNAKE':` inside a `switch` — a status/type/severity code
 *      being mapped to a colour, label or icon in the browser.
 *   2. An object literal that maps UPPER_SNAKE keys to a tag colour:
 *      `{ PENDING: 'warn', REJECTED: 'danger' }` — the same map spelled as data.
 *
 * Every such mapping belongs in the code lists (LU_* tables: UI_SEVERITY, ICON,
 * NAME_AR/NAME_EN) and reaches the screen through CodeListService and the
 * codeLabel / codeSeverity / codeIcon pipes.
 *
 * Run from Frontend: npm run find-code-switches
 */

import { readFileSync, readdirSync, statSync } from 'fs';
import { join, dirname, relative } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const appDir = join(__dirname, '..', 'src', 'app');

/**
 * Files (relative to src/app, forward slashes) allowed to keep a literal, each
 * with the reason. Empty on purpose: every switch found on 30 Aug 2026 moved
 * to the code lists. Add an entry only with a reason the reviewer would accept.
 */
const ALLOW_LIST = [
  // { file: 'demo/components/x/x.component.ts', reason: '…' },
  // The admin-tier privilege matrix (R-12, N-02.b). These switch on AdminTier
  // (SUPER_ADMIN … COUNTRY_STAFF) to decide who may grant what: authorisation
  // logic mirrored from the server, not a code→colour/label display mapping, and
  // not a list an administrator could edit. Allow-listed 22 Sep 2026 (plan W6-08).
  { file: 'demo/service/ums-auth.service.ts', reason: 'AdminTier grant matrix (mayGrantTier), authorisation logic' },
  { file: 'demo/components/ums-admin/effective-permissions/effective-permissions.component.ts', reason: 'AdminTier grant matrix, authorisation logic' },
  { file: 'demo/components/ums-admin/user-creation-wizard/user-creation-wizard.component.ts', reason: 'AdminTier grant matrix, authorisation logic' },
];

const COLOUR_WORDS = ['success', 'warning', 'warn', 'danger', 'info', 'secondary', 'red', 'green', 'orange', 'blue', 'yellow'];

/** `case 'UPPER_SNAKE':` — at least two characters, letters/digits/underscores, starting with a letter. */
const CASE_RE = /^\s*case\s+'([A-Z][A-Z0-9_]+)'\s*:/;
/** `UPPER_SNAKE: 'colour'` inside an object literal. */
const MAP_RE = new RegExp(`(?:^|[{,]\\s*)'?([A-Z][A-Z0-9_]+)'?\\s*:\\s*'(${COLOUR_WORDS.join('|')})'`);

function walk(dir, files = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, files);
    else if (name.endsWith('.ts') && !name.endsWith('.spec.ts')) files.push(full);
  }
  return files;
}

/** Strips block and line comments so a commented-out example cannot trip the check. */
function withoutComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '))
             .replace(/(^|[^:'"`])\/\/[^\n]*/g, (m, lead) => lead + ' '.repeat(m.length - lead.length));
}

const hits = [];
for (const file of walk(appDir)) {
  const rel = relative(appDir, file).replace(/\\/g, '/');
  if (ALLOW_LIST.some(a => a.file === rel)) continue;
  const lines = withoutComments(readFileSync(file, 'utf8')).split('\n');
  let switchDepth = 0;
  lines.forEach((line, i) => {
    if (/\bswitch\s*\(/.test(line)) switchDepth = 1;
    if (switchDepth && CASE_RE.test(line)) {
      hits.push(`${rel}:${i + 1}: ${line.trim()}`);
    }
    if (switchDepth && /^\s*}\s*$/.test(line) && !/case/.test(line)) {
      // A lone closing brace at the switch's indentation ends it; nested braces
      // inside a case are indented deeper and rarely stand alone at that level.
      switchDepth = 0;
    }
    const m = line.match(MAP_RE);
    if (m) {
      hits.push(`${rel}:${i + 1}: ${line.trim()}`);
    }
  });
}

if (hits.length) {
  console.error(`\nHard-coded code→colour/label mappings (${hits.length}). Move them to the code lists (UI_SEVERITY / ICON / names) and use the codeLabel / codeSeverity / codeIcon pipes:`);
  hits.forEach(h => console.error('  -', h));
  process.exit(1);
}
console.log('Code switches OK: no hard-coded code→colour/label mapping in src/app.');
